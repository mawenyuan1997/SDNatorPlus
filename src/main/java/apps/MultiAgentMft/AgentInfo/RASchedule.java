package apps.MultiAgentMft.AgentInfo;

import jade.core.AID;
import jade.core.Agent;

import java.util.ArrayList;
import java.util.Collections;

public class RASchedule {
	public Agent resourceAgent;
	public ArrayList<String> productAgents;
	public ArrayList<Integer> startTimes;
	public ArrayList<Integer> endTimes;
	public Capabilities RaCapability;
	private final int error = 0;
	/**
	 * @param resourceAgent
	 */
	public RASchedule(Agent resourceAgent) {
		this.resourceAgent = resourceAgent;
		this.productAgents = new ArrayList<String>();
		this.startTimes = new ArrayList<Integer>();
		this.endTimes = new ArrayList<Integer>();
	}

	/**
	 * @param capability of a resource agent
	 */
	public RASchedule(Capabilities capability) {
		this.RaCapability = capability;
		this.productAgents = new ArrayList<String>();
		this.startTimes = new ArrayList<Integer>();
		this.endTimes = new ArrayList<Integer>();
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		
		String total = "";
		
		for (int i = 0; i<this.productAgents.size();i++){
			total = total + " "+ this.productAgents.get(i) + " [" + this.startTimes.get(i) + "," + this.endTimes.get(i)+"];";		
		}
		
		return resourceAgent + "Schedule:" + total;
	}
	
	public boolean checkPATime(AID productAgent, int startTime, int endTime){
		String productAgentName = productAgent.getName();
		
		Integer checkStartTime;
		Integer checkEndTime;
		
		//Check to see if PA can be scheduled
		for(int i = 0; i < startTimes.size(); i++){
			checkStartTime = startTimes.get(i);
			checkEndTime = endTimes.get(i);
			
			//Check to see if the product agent has this time reserved
			if (startTime >= checkStartTime - error && endTime<=checkEndTime+error){
				if (this.productAgents.get(i).equals(productAgentName)){
					return true;
				}
				else{
					return false;
				}
			}
		}
		return false;
	}

	/** Add a product agent to the schedule
	 * @param productAgent
	 * @param startTime
	 * @param endTime
	 * @param allowMultiple
	 * @return
	 */
	public boolean addPA(AID productAgent, Integer startTime, Integer endTime, boolean allowMultiple){
		String productAgentName = productAgent.getName();
		
		if (startTime < 0 || endTime <= 0 || endTime<startTime ){
			System.out.println("End time and start time are wrong for " + resourceAgent + " for " + productAgent);
			return false;
		}
		
		//If the new product agent is the first product agent to be added
		if (productAgents.size() == 0){
			productAgents.add(productAgentName);
			startTimes.add(startTime);
			endTimes.add(endTime);
			return true;
		}
		
		
		Integer checkStartTime;
		Integer checkEndTime;
		
		//If the new part agent is scheduled after the first one and it doesn't allow multiple scheduled together
		if (!allowMultiple){
			//Check to see if PA can be scheduled
			for(int i = 0; i < startTimes.size(); i++){
				checkStartTime = startTimes.get(i);
				checkEndTime = endTimes.get(i);
				
				//If the event starts before the 
				if (startTime < checkEndTime){
					if (endTime <= checkStartTime){
						productAgents.add(i,productAgentName);
						startTimes.add(i,startTime);
						endTimes.add(i,endTime);
						return true;
					}
					else{
						System.out.println("Resource busy " + resourceAgent.getLocalName() + " for " + productAgent.getLocalName());
						return false;
					}
				}
			}
		}
		
		
		//If the new part agent is scheduled last and the resource is free
		productAgents.add(productAgentName);
		startTimes.add(startTime);
		endTimes.add(endTime);
		return true;
	}
	
	private void sortByStartTime() {
		ArrayList<Integer> newStartList = new ArrayList<Integer>(this.startTimes);
		ArrayList<Integer> newEndList = new ArrayList<Integer>(this.endTimes);
		ArrayList<String> newPA = new ArrayList<String>(this.productAgents);
		
		Collections.sort(newStartList);
		for(int sortIndex = 0;sortIndex<newStartList.size();sortIndex++){
			int startTimeSorted = newStartList.get(sortIndex);
			int index = this.startTimes.indexOf(startTimeSorted);
			newEndList.set(sortIndex, this.endTimes.get(index));
			newPA.set(sortIndex, this.productAgents.get(index));
		}
		
		this.startTimes = newStartList;
		this.endTimes = newEndList;
		this.productAgents = newPA;
	}
	
	/** Removes a product agent from the schedul
	 * @param productAgentName
	 * @param startTime
	 * @param endTime 
	 * @return 
	 */
	public boolean removePA(AID productAgent, int startTime, int endTime){
		String productAgentName = productAgent.getName();
		
		for(int index = 0; index < this.startTimes.size(); index++){
			//Find if there is a product agent scheduled for the proposed time to remove it
			if (startTime >= this.startTimes.get(index) && startTime< this.endTimes.get(index) && productAgentName.equals(this.productAgents.get(index))){
				//Remove the product agent if the proposed scheduled agent is found
				this.startTimes.remove(index);
				
				//Check if the desired end time is after the indexed end time
				if (endTime>=this.endTimes.get(index)){
					this.endTimes.remove(index);
					this.productAgents.remove(index);
				}
				//If it is not, just shorten the length of the event by moving the start time to the proposed endTime
				else{
					this.startTimes.add(index, endTime);
				}
				
				sortByStartTime();return true;
			}
		}
		
		return false;
	}

	/** Gets the amount of free time before the resource is taken again
	 * @param startTime
	 * @return
	 */
	public int getFreeTimeAmount(int startTime){
		//Large number if there is nothing after this part
		int ret = 100000;
		
		for(int i = 0; i < startTimes.size(); i++){
			if (startTimes.get(i) > startTime){
				
				//The time until the first product agent is scheduled to arrive
				if (i==0){
					ret  = startTimes.get(i) - startTime;
				}
				
				//Check to see that the last event ended
				else if (endTimes.get(i-1) <= startTime){
					ret  = startTimes.get(i) - startTime;
				}
				
				// The resource is working during this time
				else{
					ret = 0;
				}
				break;
			}
		}
		
		return ret;
	}
	
	/** Gets the next time the resource doesn't have anything scheduled
	 * @param startTime
	 * @param pathValue 
	 * @return
	 */
	public int getNextFreeTime(int startTime, int timeAction){
		
		//If there are no end times or it's after all the scheduled events, return the start time
		if (endTimes.size() == 0 || startTime > endTimes.get(endTimes.size()-1)){
			return startTime;
		}
				
		int checkEndTime = startTime+timeAction;
		
		//Check to see if the resource is working
		for(int i = 0; i < startTimes.size(); i++){
			if (startTimes.get(i) > checkEndTime){
				if (i==0 || endTimes.get(i-1)<startTime){
					return startTime;
				}
			}
			
		}		
		
		//System.out.println(ret);
		return endTimes.get(endTimes.size()-1)+1;
	}
	
}