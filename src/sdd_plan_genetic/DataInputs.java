package sdd_plan_genetic;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
/**
 * 
 * @author mingni
 * 
 */
public class DataInputs {
	
	// the directory of the data files
	static String FilePath = "C:/Users/mingni/Desktop/SDD_HL/DATA/"; 
	static String table_StyleInfo = "STYL_INFO.csv";
	static String table_ZipAland = "GH4_Gaz_zcta_national.csv";
	
	//Price, markdown and salelost 
	//SKUInfo
	HashMap<Integer, Double> styl_price = new HashMap<Integer, Double>();
	HashMap<Integer, Double> styl_markdown = new HashMap<Integer, Double>();
	HashMap<Integer, Double> styl_salelost = new HashMap<Integer, Double>();	
	
	//the land area of the delivery region
	HashMap<String, Double> land_area = new HashMap<String,Double>();
	
	public void setSKUinfo(double rate_md, double rate_sl) throws Exception{
		BufferedReader reader = new BufferedReader(new FileReader(FilePath + table_StyleInfo));
		reader.readLine(); //skip the header 
		String line = null;
        while((line=reader.readLine())!=null){ 
            String item[] = line.split(",");
            
            //col2:EI_STYL_ID; col4:price; col5:markdown; col6:salelost; col9:markdown code
            //Here only col2 and col4 as fact to input
            int StylID = Integer.parseInt(item[1]);
            double Price = Double.parseDouble(item[3]);
            int mk_cd = Integer.parseInt(item[8]);
            
            this.styl_price.put(StylID, Price);
            this.styl_salelost.put(StylID, Price*rate_sl);
            if(mk_cd == 1){
            	this.styl_markdown.put(StylID, Price*rate_md);
            } else {
            	this.styl_markdown.put(StylID, 0.0);
            }
            

        }
	}

	public void setLandArea(ArrayList<String> zipcodeList) throws Exception{
		BufferedReader reader = new BufferedReader(new FileReader(FilePath + table_ZipAland));
		reader.readLine(); //skip the header 
		HashMap<String, Double> Zip_Aland = new HashMap<String,Double>();
		String line = null;
        while((line=reader.readLine())!=null){ 
            String item[] = line.split(",");
            
            //old zip file
            //col1: zipcode; col6:ALAND_SQMI;
            //int zip = Integer.parseInt(item[0]);
            //double aland = Double.parseDouble(item[5]);
            
            //Geohash files
            //col1: zip_geohash; col7:ALAND_SQMI;
            String zip = item[0];
            double aland = Double.parseDouble(item[6]);
            
            Zip_Aland.put(zip, aland);
        }
        for(String zip:zipcodeList){
        		if (Zip_Aland.containsKey(zip)){
        			land_area.put(zip, Zip_Aland.get(zip));
        		}else{
        			land_area.put(zip, 0.0);
        		}
        }
	}
	
}
