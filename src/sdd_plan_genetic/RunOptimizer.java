package sdd_plan_genetic;

import java.io.BufferedReader;
import java.io.FileReader;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
/**
 * 
 * @author mingni
 *
 *@TODO: 
 *ship rate by 3rd part carrier
 *dimension weight?
 *discretize?
 *database optimization
 *and
 *Pseudo data input incomplete
 */

public class RunOptimizer {
	
	static PlanOptimizer plan = new PlanOptimizer();
	
	//the lines of sdd demand to be loaded

	
	// the directory of the data files
	static String FilePath = "C:/Users/mingni/Desktop/SDD_HL/DATA/";
	//static String table_SDDDemand = "SDD_STYL_DEMAND.csv";
	static String table_SDDDemand = "SDD_STYL_DEMAND_SAMPLE_V1_ADDGeoHash_NOV29.csv";
	static String table_StrList = "STRS_LIST.csv";
	
	//table distance from zip to store 
	//on the database 
	static String table_DistanceStrZip = "DB2ADMIN.ZIP_NODE_MAPPING_CHI_GH4";
	
	static HashMap<Integer, Integer> stylIndex = new HashMap<Integer, Integer>();
	static HashMap<Integer, Integer> indexStyl = new HashMap<Integer, Integer>();
	
	static HashMap<String, Integer> zipIndex = new HashMap<String, Integer>();
	static HashMap<Integer, String> indexZip = new HashMap<Integer, String>();
	
	static HashMap<Integer, Integer> storeIndex = new HashMap<Integer, Integer>();
	static HashMap<Integer, Integer> indexStore = new HashMap<Integer, Integer>();
	
	static HashMap<String, Integer> dayIndex = new HashMap<String, Integer>();
	static HashMap<Integer, String> indexDay = new HashMap<Integer, String>();
	
	public RunOptimizer(Connection con, Integer NLines, Integer NStores) throws Exception{
		this.DataInputOptimizer(con, NLines, NStores);
		plan.cplexRun();
	}
	
	public void DataInputOptimizer(Connection con, Integer NLines, Integer NStores) throws Exception{
		//Pseudo data input
		double rate_md = 0.3;
		double rate_sl = 0.5;
		double rate_costpermile = 0.8;
		
		plan.opr_fee = 250.0;
		//plan.opr_fee = 25.00;
		plan.veh_working_time = 8.0;
		plan.mean_speed=20.0;
		plan.stop_time = 0.083333;
		plan.veh_cap = 30;
		
		plan.a = 1.148;
		//plan.b = 4.922;
		plan.b = 0;
		
		//get the index maps ready to use 
		this.setIndex(NLines, NStores);
		
		plan.p_style = stylIndex.size();
		plan.p_day = dayIndex.size();
		plan.p_str = storeIndex.size();
		plan.p_zone = zipIndex.size();
		
		System.out.println("Style: "+plan.p_style);
		System.out.println("Time Range: "+plan.p_day);
		System.out.println("Store: "+plan.p_str);
		System.out.println("Zone: "+plan.p_zone);
		
		//get the sdd demand
		this.getSDDDemand(NLines);
		
		//get the store sdd setup cost
		this.getStrSetup();
		
		//get Price, markdown and salelost, plus the land area of zipcode
		DataInputs dp = new DataInputs();
		dp.setSKUinfo(rate_md,rate_sl);
		ArrayList<String> zipList = new ArrayList<String>(zipIndex.keySet());
		dp.setLandArea(zipList);
		//price
		plan.price = new double[plan.p_style];
		for(int j=0;j<plan.p_style;j++){
			plan.price[j] = 0.0;
		}
		for(int styl:stylIndex.keySet()){
			int j = stylIndex.get(styl);
			if(dp.styl_price.containsKey(styl)){
				plan.price[j] = dp.styl_price.get(styl);
			}
		}
		//markdown
		plan.markd= new double[plan.p_style];
		for(int j=0;j<plan.p_style;j++){
			plan.markd[j] = 0.0;
		}
		for(int styl:stylIndex.keySet()){
			int j = stylIndex.get(styl);
			plan.markd[j] =dp.styl_markdown.get(styl);
		}
		//salelost
		plan.salel= new double[plan.p_style];
		for(int j=0;j<plan.p_style;j++){
			plan.salel[j] = 0.0;
		}
		for(int styl:stylIndex.keySet()){
			int j = stylIndex.get(styl);
			plan.salel[j] =dp.styl_salelost.get(styl);
		}
		//land area
		plan.A = new double[plan.p_zone];
		for(int z=0;z<plan.p_zone;z++){
			plan.A[z] = 0.0;
		}
		for(String zip:dp.land_area.keySet()){
			int z = zipIndex.get(zip);
			plan.A[z] = dp.land_area.get(zip);
		}
		
		//Get the store zip distance and calculate the carrier cost based on distance
		plan.distance = new double[plan.p_str][plan.p_zone];
		plan.ship_rate = new double[plan.p_str][plan.p_zone];
		for(int k=0; k<plan.p_str;k++){
			for(int z=0;z<plan.p_zone;z++){
				plan.distance[k][z] = 99.0; // 99 mile for default setting if there is not data avaible
				plan.ship_rate[k][z] = 99.0;
			}
		}
		for(int str:storeIndex.keySet()){
			for(String zip:zipIndex.keySet()){
				int k = storeIndex.get(str);
				int z = zipIndex.get(zip);
				plan.distance[k][z] = this.getDistanceStrZip(con, zip, str);
				plan.ship_rate[k][z] = rate_costpermile*plan.distance[k][z];
			}
		}
		
		//initialize the p_h, H[] and H_sq[]
		this.ParamH();
		
		//initialize the big M
		plan.M = this.getM();
		System.out.println("==========================");
		System.out.println("Parameter loading finish. Optimizer run.");
	}
	
	//prepare for the index
	public void setIndex(Integer NLines, Integer NStores) throws Exception{
	//public void setIndex() throws Exception{
		//Read the sdd demand table
        BufferedReader reader = new BufferedReader(new FileReader(FilePath + table_SDDDemand));
        reader.readLine(); //skip the header 
        String line = null; 
        
        HashSet<Integer> StylIDSet = new HashSet<Integer>();
        HashSet<String> DateSet = new HashSet<String>();
        HashSet<String> ZipSet = new HashSet<String>();
        HashSet<Integer> StrSet = new HashSet<Integer>();
        
        int nl = 0;
        if(NLines !=0){
        	while(nl<NLines & (line=reader.readLine())!=null){ 
                String item[] = line.split(",");
                //co1:stylID; col2:DATE; col3:zipcode;
                //StylIDSet.add(Integer.parseInt(item[0]));
                //DateSet.add(item[1]);
                //ZipSet.add(Integer.parseInt(item[2]));
                //co1:stylID; col2:DATE; col7:GH4;
                StylIDSet.add(Integer.parseInt(item[0]));
                DateSet.add(item[1]);
                ZipSet.add(item[6]);
                nl++;
            }
        }else {
        	while((line=reader.readLine())!=null){ 
                String item[] = line.split(",");
                //co1:stylID; col2:DATE; col3:zipcode;
                //StylIDSet.add(Integer.parseInt(item[0]));
                //DateSet.add(item[1]);
                //ZipSet.add(Integer.parseInt(item[2]));
                //co1:stylID; col5:MONTH; col6:ZIPAGG;
                StylIDSet.add(Integer.parseInt(item[0]));
                DateSet.add(item[1]);
                ZipSet.add(item[6]);
            }
        }
        
        //if(NLines != null){
        	//StylIDSet = StylIDSet.
        //}
        //set style index
        int ind=0;
        for(int Styl:StylIDSet){
        	stylIndex.put(Styl, new Integer(ind));
        	indexStyl.put(new Integer(ind), Styl);
        	ind++;
        }
        
        //set Zipcode index
        ind=0;
        for(String zip:ZipSet){
        	zipIndex.put(zip, new Integer(ind));
        	indexZip.put(new Integer(ind), zip);
        	ind++;
        }
        
        //set date index
        ind=0;
        for(String Date: DateSet){
        	dayIndex.put(Date, new Integer(ind));
        	indexDay.put(new Integer(ind),Date);
        	ind++;
        }    
        
        reader = new BufferedReader(new FileReader(FilePath + table_StrList));
        reader.readLine(); //skip the header 
        line = null;
        int ns=0;
        if (NStores!=0){
            while(ns<NStores & (line=reader.readLine())!=null){ 
                String item[] = line.split(",");
                //col2:StrID
                StrSet.add(Integer.parseInt(item[1]));
                ns++;
            }
        }else{
            while((line=reader.readLine())!=null){ 
                String item[] = line.split(",");
                //col2:StrID
                StrSet.add(Integer.parseInt(item[1]));
            }
        }

        
        //set store index
        ind=0;
        for(int StrId:StrSet){
        	storeIndex.put(StrId, new Integer(ind));
        	indexStore.put(new Integer(ind), StrId);
        	ind++;
        }
	}
	
	//get store SDD setup cost
	private void getStrSetup() throws Exception{
		plan.sdd_setup_cost = new double[plan.p_str];
		for(int k=0;k<plan.p_str;k++){
			plan.sdd_setup_cost[k] = 0.0;
		}
		BufferedReader reader = new BufferedReader(new FileReader(FilePath + table_StrList));
        reader.readLine(); //skip the header 
        String line = null;
        while((line=reader.readLine())!=null){ 
            String item[] = line.split(",");
            //col2:StrID col7:setup cost
           int index = Integer.parseInt(item[1]);
            if(storeIndex.containsKey(index)){
            	 int k = storeIndex.get(index);
                 //plan.sdd_setup_cost[k] = Double.parseDouble(item[6]);
            	 //test
                 plan.sdd_setup_cost[k] = Double.parseDouble(item[6])/1000;
            }
        }
	}
	
	
	//get the SDD demand from the data
	public void getSDDDemand(Integer NLines) throws Exception{
		//initialize the sdd demand data set
		plan.sdd_demand = new int[plan.p_style][plan.p_day][plan.p_zone];
		for(int j=0;j<plan.p_style;j++){
			for(int t=0;t<plan.p_day;t++){
				for(int z=0;z<plan.p_zone;z++){
					plan.sdd_demand[j][t][z] = 0;
				}
			}
		}
		
		//Read the sdd demand table
        BufferedReader reader = new BufferedReader(new FileReader(FilePath + table_SDDDemand));
        reader.readLine(); //skip the header 
        String line = null; 
        
        int nl=0;
        if(NLines != 0){
            while(nl<NLines & (line=reader.readLine())!=null){ 
                String item[] = line.split(",");
                //col1:stylID; col2:DATE; col3:zipcode; col4:demand;
                //int StylID = Integer.parseInt(item[0]);
                //String Date = item[1];
                //int zip = Integer.parseInt(item[2]); 
                //int demand = Integer.parseInt(item[3]);
                
                //co1:stylID; col2:DATE; col7:GH4; col4:demand
                int StylID = Integer.parseInt(item[0]);
                String Date = item[1];
                String zip = item[6]; 
                int demand = Integer.parseInt(item[3]);
                
                int j = stylIndex.get(StylID);
                int t = dayIndex.get(Date);
                int z = zipIndex.get(zip);
                plan.sdd_demand[j][t][z] = demand;
                nl++;
            }  
        }else{
            while((line=reader.readLine())!=null){ 
                String item[] = line.split(",");
                //col1:stylID; col2:DATE; col3:zipcode; col4:demand;
                //int StylID = Integer.parseInt(item[0]);
                //String Date = item[1];
                //int zip = Integer.parseInt(item[2]); 
                //int demand = Integer.parseInt(item[3]);
                
                //co1:stylID; col2:DATE; col7:GH4; col4:demand
                int StylID = Integer.parseInt(item[0]);
                String Date = item[1];
                String zip = item[6]; 
                int demand = Integer.parseInt(item[3]);
                
                int j = stylIndex.get(StylID);
                int t = dayIndex.get(Date);
                int z = zipIndex.get(zip);
                plan.sdd_demand[j][t][z] = demand;
            }  
        }
  
	}
	
	//get the store zip ditance
	private double getDistanceStrZip(Connection con, String zip, int str) throws SQLException{
		double distance = 0.0;
		Statement stmt = con.createStatement();
		//String query = "SELECT DISTANCE FROM "+ table_DistanceStrZip
		//		+" WHERE ZIPCODE = "+ zip+" AND STR_ID = "+str;
		String query = "SELECT DISTANCE_BING FROM "+ table_DistanceStrZip
				+" WHERE ZIP_GH4 ='"+ zip +"' AND STR_ID = "+str;		
		System.out.println(query);
		ResultSet rs = stmt.executeQuery(query);
		if(rs.next() && rs.getString(1) != null){
			distance = Double.valueOf(rs.getString(1)).doubleValue();
		} else distance = 99.0;
		rs.close();
		stmt.close();
		return(distance);
	}
	
	/*
	private HashMap<Integer, Double> getDistanceStrZip(Connection con) throws SQLException{
		HashMap<Integer, Double> 
		double distance = 0.0;
		Statement stmt = con.createStatement();
		String query = "SELECT DISTANCE FROM "+ table_DistanceStrZip
				+" WHERE ZIPCODE = "+ zip+" AND STR_ID = "+str;
		System.out.println(query);
		ResultSet rs = stmt.executeQuery(query);
		if(rs.next() && rs.getString(1) != null){
			distance = Double.valueOf(rs.getString(1)).doubleValue();
		} else distance = 99.0;
		rs.close();
		stmt.close();
		return(distance);
	}
	*/
 
	/*
	 *more explain comments might need
	 */
	private void ParamH(){
		double h_sq_add[][] = new double[plan.p_day][plan.p_zone];
		for(int t=0;t<plan.p_day;t++){
			for(int z=0;z<plan.p_zone;z++){
				h_sq_add[t][z] = 0.0;
				for(int j=0;j<plan.p_style; j++){
					h_sq_add[t][z]+=(double) plan.sdd_demand[j][t][z];
				}
			}
		}
		
		// get the maximum value of h_sq_add[]
		double h_sq_max =0;
		for(int t=0;t<plan.p_day;t++){
			for(int z=0;z<plan.p_zone;z++){
				if(h_sq_add[t][z]>h_sq_max) h_sq_max= h_sq_add[t][z];
			}
		}

		//initialize the p_h
		Double h_max = Math.sqrt(h_sq_max);
		plan.p_h = h_max.intValue()+2; //NOTE: + 2 in order to include the zero and h_max into h[p_h]
		////initialize the h[p_h],and h_sq[p_h]
		plan.H = new double[plan.p_h];
		plan.H_sq = new double[plan.p_h];
		for(int h=0;h<plan.p_h;h++){ 
			plan.H[h] = (double) h;
			plan.H_sq[h] = (double) h*h;
		}
	}
	
	//get the value for "big" integer M
	private int getM(){
		int M =0;
		for(int j=0;j<plan.p_style; j++){
			for(int t=0;t<plan.p_day;t++){
				for(int z=0;z<plan.p_zone;z++){
					M += plan.sdd_demand[j][t][z];
				}
			}
		}
		return M;
	}

}
