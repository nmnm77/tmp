package sdd_plan_genetic;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;

/*
 * TODO:incorparate the seasonal costs with daily cost in PlanOptimizer.java
 * fitness = seasonal profit
 */
public class FitnessCalc {
	


    // Calculate inidividuals fittness by comparing it to our candidate solution
    static int getFitness(Individual individual) {
        int fitness = 0;
        
        //get the fleet size from the individuals genes 0
        int nfleet = individual.getGene(0);
        
        //store combination
        HashSet<Integer> selectedStr = new HashSet<Integer>();
        
        
        // Loop through the individuals genes as store combination
        for (int i = 1; i < individual.size(); i++) {
        		if(individual.getGene(i) == 1){
        			selectedStr.add(i);
        		}
        }
        
        
        return fitness;
    }

	
    
    
    // Get optimum fitness
    static int getMaxFitness() {
        int maxFitness = solution.length;
        return maxFitness;
    }

}
