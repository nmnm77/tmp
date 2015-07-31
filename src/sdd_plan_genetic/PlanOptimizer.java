package sdd_plan_genetic;
/**
 * 
 * @author mni
 * @since 2015-02-14
 */


import ilog.concert.*;
import ilog.cplex.*;

public class PlanOptimizer {
	/*********************************************
	 *********** output
	 *********************************************/
	
	//double out_turnover = 0.0;
	//double out_sdd_setup = 0.0;
	//double out_veh_cost = 0.0;
	//double out_carrier_cost = 0.0;
	//double out_markdown = 0.0;
	//double out_salelost = 0.0;
	
	//double out_nfleet = 0.0;
	
	/*********************************************
	 *********** Indice
	 *********************************************/
	int p_style = 0;
	int p_str = 0;
	int p_zone = 0;
	
	//int p_h = 0;
	
	/*********************************************
	 *********** Parameters
	 *********************************************/
	int nfleet = 0;
	
	double ship_rate[][] = new double[p_str][p_zone];
	
	double distance[][] = new double[p_str][p_zone];

	double A[] = new double[p_zone];

	
	double veh_working_time = 0.0;
	double mean_speed=0.0;
	double stop_time = 0.0;
	int veh_cap = 0;
	

	// the linear parameters for sdd orders and sold item quantity
	double a = 0.0;
	double b = 0.0;
	
	/*********************************************
	 *********** Variables
	 *********************************************/		

	int sdd_demand[][] = new int[p_style][p_zone];
	
	double salel[] = new double[p_style];
	
	double price[] = new double[p_style];
	
	int V_str[][] = new int[p_style][p_str];
	
	//item-based store capacity
	//int str_cpct[] = new int[p_str];
	
	//double H[] = new double[p_h];
	//double H_sq[] = new double[p_h];
	
	/*********************************************
	 *********** Run Method
	 *********************************************/
	public void cplexRun(){
		try{
			//calculate the square root of A[]
			double A_sqr[] = new double[p_zone];
			for(int z=0;z<p_zone;z++){
				A_sqr[z] = Math.sqrt(A[z]);
			}
			
			IloCplex cplex = new IloCplex();
			
			/*********************************************
			 *********** Decision variables
			 *********************************************/			
			//Total number of sales lost of SKU j 
			IloNumVar beta[] = new IloNumVar[p_style];
			for(int j=0;j<p_style;j++){
				beta[j] = cplex.numVar(0, Double.MAX_VALUE);
			}
			
			//SDD orders by own vehicle at day t for area z from store k
			IloNumVar x[][] = new IloNumVar[p_str][p_zone];
			for(int k=0;k<p_str;k++){
				for(int z=0;z<p_zone;z++){
					x[k][z] = cplex.numVar(0, Double.MAX_VALUE);
				}
			}
			
			//SDD orders by 3rd part carriers at day t for area z from store k
			IloNumVar y[][] = new IloNumVar[p_str][p_zone];
			for(int k=0;k<p_str;k++){
				for(int z=0;z<p_zone;z++){
					y[k][z] = cplex.numVar(0, Double.MAX_VALUE);
				}
			}
			
			//the number of SDD orders at day t for area z from store k
			IloNumVar n[][] = new IloNumVar[p_str][p_zone];
			for(int k=0;k<p_str;k++){
				for(int z=0;z<p_zone;z++){
					n[k][z] = cplex.numVar(0, Double.MAX_VALUE);
				}
			}
			
			//the optimal of traveling distance to fulfill n_t orders from store network. (Continuous Approximation)
			IloNumVar l = cplex.numVar(0, Double.MAX_VALUE);
			
			//Number of SKU j will be fulfilled at day t for area z from store k
			IloNumVar nitem[][][] = new IloNumVar[p_style][p_str][p_zone];
			for(int j=0;j<p_style;j++){
				for(int k=0;k<p_str;k++){
					for(int z=0;z<p_zone;z++){
						nitem[j][k][z] = cplex.numVar(0, Double.MAX_VALUE); 
					}
				}
			}
			
			//d.v. to represent the square root of x[t][k][z]
			/*
			IloNumVar q[][][] = new IloNumVar[p_day][p_str][p_zone];
			for(int t=0;t<p_day;t++){
				for(int k=0;k<p_str;k++){
					for(int z=0;z<p_zone;z++){
						q[t][k][z] = cplex.numVar(0, Double.MAX_VALUE);
					}
				}
			}
			
			//binary d.v to select the proper q[t][k][z] to approximate the x[t][k][z]
			IloNumVar f[][][][] = new IloNumVar[p_day][p_str][p_zone][p_h];
			for(int t=0;t<p_day;t++){
				for(int k=0;k<p_str;k++){
					for(int z=0;z<p_zone;z++){
						for(int h=0;h<p_h;h++){
							f[t][k][z][h] = cplex.boolVar();
						}
					}
				}
			}
			*/
			/*-------------------------DVs to output-----------------------*/
			//turnover
			IloLinearNumExpr turnover = cplex.linearNumExpr();
			for(int j=0;j<p_style;j++){
				for(int k=0;k<p_str;k++){
					for(int z=0;z<p_zone;z++){
						turnover.addTerm(price[j], nitem[j][k][z]);
					}
				}
			}
			
			//carrier_cost
			IloLinearNumExpr carrier_cost = cplex.linearNumExpr();
			for(int k=0;k<p_str;k++){
				for(int z=0;z<p_zone;z++){
					carrier_cost.addTerm(-ship_rate[k][z], y[k][z]);
				}
			}
			
			//sale lost
			IloLinearNumExpr salelost = cplex.linearNumExpr();
			for(int j=0;j<p_style;j++){
				salelost.addTerm(-salel[j], beta[j]);
			}
			
			// store inventory next day
			//IloNumVar V_str_next[][] = new IloNumVar[p_style][p_str];
			//for(int j=0;j<p_style;j++){
			//	for(int k=0;k<p_str;k++){
			//		V_str_next[j][k] = cplex.numVar(0, Double.MAX_VALUE);
			//	}
			//}
			
			//KPI for finest individuals
			/*
			//daily sale by stores
			IloLinearNumExpr DailySalebyStore[] = new IloLinearNumExpr[p_str];
			for(int k=0;k<p_str;k++){
				DailySalebyStore[k] = cplex.linearNumExpr();
				for(int j=0;j<p_style;j++){
					for(int z=0;z<p_zone;z++){
						DailySalebyStore[k].addTerm(1.0, nitem[j][k][z]);
					}
				}
			}
			
			//daily order fulfilled by own vehicle
			IloLinearNumExpr DailyFulbyVeh = cplex.linearNumExpr();
			for(int k=0;k<p_str;k++){
				for(int z=0;z<p_zone;z++){
					DailyFulbyVeh.addTerm(1.0, x[k][z]);
				}
			}
			
			//daily order fulfilled by own vehicle
			IloLinearNumExpr DailyFulbyCarr = cplex.linearNumExpr();
			for(int k=0;k<p_str;k++){
				for(int z=0;z<p_zone;z++){
					DailyFulbyCarr.addTerm(1.0, y[k][z]);
				}
			}
			*/
			/*------------------------------------------------------------------------*/
			/*-------------------------Objective-----------------------*/
			/*------------------------------------------------------------------------*/
			//Maximize the SDD profit/revenue
			IloLinearNumExpr object = cplex.linearNumExpr();
			object.add(turnover);
			object.add(carrier_cost);
			object.add(salelost);
			
			cplex.addMaximize(object);

			/*------------------------------------------------------------------------*/
			/*-------------------------Constrains-----------------------*/
			/*------------------------------------------------------------------------*/
			//Number of sku j will be fulfilled or purchased meets the demand
			
			IloLinearNumExpr expr_jz[][] = new IloLinearNumExpr[p_style][p_zone];
			for(int j=0;j<p_style;j++){
				for(int z=0;z<p_zone;z++){
					expr_jz[j][z] = cplex.linearNumExpr();
					for(int k=0;k<p_str;k++){
						expr_jz[j][z].addTerm(1.0, nitem[j][k][z]);
					}
					cplex.addLe(expr_jz[j][z], sdd_demand[j][z]);
				}
			}
			
			//total number of sales lost for the SDD demand
			IloLinearNumExpr expr_j[] = new IloLinearNumExpr[p_style];
			int sdd_demand_j[] = new int[p_style];
			for(int j=0;j<p_style;j++){
				expr_j[j] = cplex.linearNumExpr();
				sdd_demand_j[j] = 0;
				for(int z=0;z<p_zone;z++){
					sdd_demand_j[j] =+ sdd_demand[j][z];
					
					for(int k=0;k<p_str;k++){
						expr_j[j].addTerm(-1.0, nitem[j][k][z]);
					}
				}
				cplex.addGe(beta[j],cplex.sum(sdd_demand_j[j], expr_j[j]));
			}
			
			//inventory balance
			IloLinearNumExpr expr_jk[][] = new IloLinearNumExpr[p_style][p_str];
			for(int j=0;j<p_style;j++){
				for(int k=0;k<p_str;k++){
					expr_jk[j][k] = cplex.linearNumExpr();
					for(int z=0;z<p_zone;z++){
						expr_jk[j][k].addTerm(1.0, nitem[j][k][z]);
					}
					cplex.addLe(expr_jk[j][k], V_str[j][k]);
				}
			}
			
			//Estimate the number of sdd order 
			//Linear relationship
			IloLinearNumExpr expr_kz[][] = new IloLinearNumExpr[p_str][p_zone];
			for(int z=0;z<p_zone;z++){
				for(int k=0;k<p_str;k++){
					expr_kz[k][z] = cplex.linearNumExpr();
					for(int j=0;j<p_style;j++){
						expr_kz[k][z].addTerm(1.0, nitem[j][k][z]);
					}
					cplex.addEq(cplex.sum(cplex.prod(a, n[k][z]), b), expr_kz[k][z]);
					cplex.addEq(n[k][z], cplex.sum(x[k][z], y[k][z]));
				}
			}
			
			//Number of trunks used for SDD orders fulfillment
			//(By Capacitated Vehicle Routing Problem CVRP)
			IloLinearNumExpr expr = cplex.linearNumExpr();
			for(int k=0;k<p_str;k++){
				for(int z=0;z<p_zone;z++){
					expr.addTerm(2*distance[k][z]/veh_cap, x[k][z]);
					expr.addTerm(0.57* A_sqr[z],x[k][z]); //q[t][k][z]
				}
			}
			cplex.addEq(l, expr);
			
			//
			expr = cplex.linearNumExpr();
			for(int k=0;k<p_str;k++){
				for(int z=0;z<p_zone;z++){
					expr.addTerm(stop_time/veh_working_time, x[k][z]);
				}
			}
			expr.addTerm(1/(mean_speed*veh_working_time),l);
			cplex.addLe(expr, nfleet);
			
			/*
			//Estimate the square root of x[t][k][z]
			IloLinearNumExpr expr_tkz_v2[][][] = new IloLinearNumExpr[p_day][p_str][p_zone];
			IloLinearNumExpr expr_tkz_v3[][][] = new IloLinearNumExpr[p_day][p_str][p_zone];
			for(int t=0;t<p_day;t++){
				for(int k=0;k<p_str;k++){
					for(int z=0;z<p_zone;z++){
						expr_tkz[t][k][z] = cplex.linearNumExpr();
						expr_tkz_v2[t][k][z] = cplex.linearNumExpr();
						expr_tkz_v3[t][k][z] = cplex.linearNumExpr();
						for(int h=0;h<p_h;h++){
							expr_tkz[t][k][z].addTerm(H_sq[h], f[t][k][z][h]);
							expr_tkz_v2[t][k][z].addTerm(H[h], f[t][k][z][h]);
							expr_tkz_v3[t][k][z].addTerm(1.0, f[t][k][z][h]);
						}
						cplex.addGe(expr_tkz[t][k][z],x[t][k][z]);
						cplex.addGe(q[t][k][z], expr_tkz_v2[t][k][z]);
						cplex.addEq(expr_tkz_v3[t][k][z], 1.0);
					}
				}
			}
			*/
			
			/*------------------------------------------------------------------------*/
			/*-------------------------Solve the model-----------------------*/
			/*------------------------------------------------------------------------*/
			//Set not simdisplay
			//cplex.setParam(IloCplex.Param.Simplex.Display, 0);
			
			//set the MIP Interval
			cplex.setParam(IloCplex.Param.MIP.Interval, (long) 0.1);
			
			if(cplex.solve()){
				//System.out.println("obj = "+cplex.getObjValue());
				//System.out.println("=========================================");
				double out_profit = 0.0;
				out_profit = cplex.getObjValue();
				
				int V_str_next[][] = new int[p_style][p_str];
				for(int j=0;j<p_style;j++){
					for(int k=0;k<p_str;k++){
						for(int z=0;z<p_zone;z++){
							V_str_next[j][k] += cplex.getValue(nitem[j][k][z]);
						}
					}
				}
			}
			else{
				System.out.println("The problem can not be solved");
			}
			
		}
		catch(IloException exc){
			exc.printStackTrace();
		}
	}


}
