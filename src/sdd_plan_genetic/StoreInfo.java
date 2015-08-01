package sdd_plan_genetic;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class StoreInfo {
	//on the database 
	static String table_StrList = "DB2ADMIN.STRS_LIST";
	
	//Store ID(s) set
	HashSet<Integer> strIDSet = new HashSet<Integer>();
	
	//gene index function to store id
	HashMap<Integer,Integer> strIDsMap = new HashMap<Integer, Integer>();
	
	//stores capacity 
	HashMap<Integer,Integer> strCap = new HashMap<Integer, Integer>();
	
	//stores setup cost
	HashMap<Integer,Double> strSetup = new HashMap<Integer, Double>();
	
	
    
    public void getStrInfo (Connection con,HashSet<Integer> selectedStr) throws Exception{
    	int strIndex;
    	int strID;
    	int strcap;
    	double setupcost;
    	String storeIndex = createStringList(selectedStr);
    	
		Statement stmt = con.createStatement();
		String query = "SELECT INDMN, EI_STR_ID, CAPACITY, SETUP FROM "+ table_StrList
				+" WHERE INDMIN IN "+ storeIndex;		
		ResultSet rs = stmt.executeQuery(query);
		while (rs.next() && rs.getString(4) != null) {
			strIndex = Integer.valueOf(rs.getString(1)).intValue();
			strID = Integer.valueOf(rs.getString(2)).intValue();
			strcap = Integer.valueOf(rs.getString(3)).intValue();
			setupcost = Double.valueOf(rs.getString(4)).doubleValue();
			
			strIDsMap.put(strIndex, strID);
			strIDSet.add(strID);
			strCap.put(strID, strcap);
			strSetup.put(strID, setupcost);
		}
		rs.close();
		stmt.close();
    }
    

	/**
	 * Create the (STRING) list from the List
	 * @param list The Input HashSet
	 * @return String list
	 */
	public static String createStringList(HashSet<Integer> list){
		String strList = "(";
		for(Integer key : list)
			strList += key.toString() + ",";
		return strList.substring(0, strList.length()-1) + ")";
	}
	

}
