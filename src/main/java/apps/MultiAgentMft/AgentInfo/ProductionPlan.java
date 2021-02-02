package apps.MultiAgentMft.AgentInfo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;

public class ProductionPlan implements Serializable{

	private static final long serialVersionUID = -5104843816229359520L;
	private ArrayList<HashSet<PhysicalProperty>> setList;
	
	public ProductionPlan() {	
		this.setList = new ArrayList<HashSet<PhysicalProperty>>();
		HashSet<PhysicalProperty> initialSet = new HashSet<PhysicalProperty>();
		this.setList.add(initialSet);
	}

	@Override
	public String toString() {
		String output = "";
		for (HashSet<PhysicalProperty> set:this.setList){
			output = output + "{";
			for (PhysicalProperty s:set){
				output = output + s + ",";
			}
			output = output + "},";
		}
		
		return output;
	}


	public void add(PhysicalProperty property){
		this.setList.get(this.setList.size()-1).add(property);
	}
	
	public void addNewSet(PhysicalProperty property){
		HashSet<PhysicalProperty> newSet = new HashSet<PhysicalProperty>();
		newSet.add(property);
		this.setList.add(newSet);
	}
	
	public void addNewSet(HashSet<PhysicalProperty> propertySet){
		this.setList.add(propertySet);
	}
	
	public HashSet<PhysicalProperty> getLastSet(){
		return this.setList.get(this.setList.size()-1);
	}
	
	public ArrayList<HashSet<PhysicalProperty>> getSetList(){
		return this.setList;
	}
}
