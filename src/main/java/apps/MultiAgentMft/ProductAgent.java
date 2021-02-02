package apps.MultiAgentMft;

import apps.MultiAgentMft.AgentInfo.*;
import apps.MultiAgentMft.AgentMsg.*;
import com.google.common.base.Function;
import edu.uci.ics.jung.algorithms.shortestpath.DijkstraShortestPath;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.UnreadableException;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class ProductAgent extends Agent {

	private final int explorationWaitTime = 150;
	private final int planningWaitTime = 50;
	private final int nextExecutionStartTime = 500;
	private final int StartBidTime = 300000;
	private final int ActionTimeout = 15000;
	private final int BidTimeChange = 300000;
	private final int MaxBidTime = 910000;
	private final int MaxScheduledEvents = 100;

	private ProductionPlan productionPlan;
	private ProductHistory productHistory;
	private EnvironmentModel environmentModel, newEnvironmentModel;
	private PAPlan plan, newPlan;
	private ArrayList<Bid> bids;
	private int bidTime = StartBidTime;
	private int lastActionQueriedTime;


	@Override
	protected void setup(){
		Object[] args = getArguments();
		assert args.length == 3 && args[0] instanceof AID
								&& args[1] instanceof ProductState
								&& args[2] instanceof ProductionPlan;
		System.out.println("PA "+getLocalName()+" setup");
		ProductState startingState = (ProductState) args[1];
		AID startingResource = (AID) args[0];
		productionPlan = (ProductionPlan) args[2];
		productHistory = new ProductHistory(getAID(), startingState, startingResource);
		environmentModel = new EnvironmentModel(getAID(), startingState, startingResource); // Environment Model
		plan = new PAPlan(getAID()); //Agent Plan
		environmentModel.update(startingState); //Set the starting state in the belief model

		addBehaviour(new DecisionDirector("Execution"));
		addBehaviour(new AcceptSystemOutput());
	}


	//================================================================================
	// Dictate what to do next. Start this behavior by giving it a completed task (exploration, planning, and execution).
	//================================================================================
	private class DecisionDirector extends OneShotBehaviour {

		private static final long serialVersionUID = 6957978387141807023L;
		private String finishedTask;

		public DecisionDirector(String finishedTask) {
			this.finishedTask = finishedTask;
		}

		@Override
		public void action() {
			if (finishedTask.equals("Exploration")) {
				if (newEnvironmentModel.isEmpty()) {
					exit();
				}
				else {
					environmentModel.clear();
					environmentModel.update(newEnvironmentModel,newEnvironmentModel.getCurrentState());
					newEnvironmentModel.clear();
					myAgent.addBehaviour(new Planning());
					System.out.println(getLocalName()+" begins planning"+", "+getCurrentTime());
				}
			}
			else if (finishedTask.equals("Planning")) {
				if (newPlan.isEmpty(getCurrentTime())) {exit();}
				else {
					plan = newPlan;
					myAgent.addBehaviour(new Execution());
					System.out.println(getLocalName()+" begins execution"+", "+getCurrentTime());
				}
			}
			else if (finishedTask.equals("Execution")) {
				if (getDesiredProperties().isEmpty()) {exit();}
				else {
					myAgent.addBehaviour(new Exploration());
					System.out.println(getLocalName()+" begins exploration"+", "+getCurrentTime());
				}
			}
		}
	}

	private void exit() {
		System.out.println(this.getLocalName() + " called Exit Plan");
		ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
		msg.addReceiver(new AID("agentLauncher@192.168.1.26:10207/JADE", true));
		msg.setContent(""+getCurrentTime());
		send(msg);
	}

	//================================================================================
	// Exploration
	//================================================================================
	private class Exploration extends OneShotBehaviour {
		private static final long serialVersionUID = 7631418228880040041L;
		@Override
		public void action() {
			//Initialize bid collection, current state, and a new environment model
			bids = new ArrayList<Bid>();
			ProductState currentState = productHistory.getCurrentState();
			newEnvironmentModel = new EnvironmentModel(myAgent.getAID(),currentState,productHistory.getLastEvent().getEventAgent()); //New environment model
			//Get the RA in charge of starting the bid exploration
			AID contactRA = productHistory.getLastEvent().getEventAgent();
			//Ask for bids for each desired property
			for (PhysicalProperty desiredProperty : getDesiredProperties()){
				System.out.println(getLocalName()+" ask for "+desiredProperty);
				Bid bid = new Bid();
				bid.addVertex(currentState);
				//Ask for bids in the future (allow exploration/waiting time)
				int timeoutOffsets = explorationWaitTime+planningWaitTime+nextExecutionStartTime;
				//Create a new bid request
				RequestBid bidRequest = new RequestBid(myAgent.getAID(), desiredProperty, currentState, bidTime+timeoutOffsets,
						bid, getCurrentTime()+timeoutOffsets);
				//Allow for bid acceptance
				AcceptBids acceptBidsBehaviour = new AcceptBids();
				addBehaviour(acceptBidsBehaviour);
				//Query for start of exploration (request for bids)
				sendMsg(ACLMessage.REQUEST, contactRA, bidRequest);
				System.out.println(getLocalName()+" sends to " + contactRA.getLocalName() + " bid request" );
				//Wait for bids to come in
				addBehaviour(new CheckExploration(myAgent,explorationWaitTime,acceptBidsBehaviour));
				//Remove this behavior (CheckExploration will restart this process if possible/necessary)
				removeBehaviour(this);
			}
		}
	}

	// Waits for bids from the RAs
	private class AcceptBids extends CyclicBehaviour {
		private static final long serialVersionUID = 8202029907040536901L;
		@Override
		public void action() {
			//Obtain any submitted bids and add them to the list
			MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.INFORM);
			ACLMessage msg = myAgent.receive(mt);
			if(msg != null){
				try {
					Object msgObj = msg.getContentObject();
					if (msgObj instanceof Bid) {
						bids.add((Bid) msg.getContentObject());
						System.out.println(getLocalName() + " end Exploration, " + getCurrentTime() + ", bid size: " + ((Bid) msg.getContentObject()).getEdgeCount());
					} else {
						putBack(msg);
					}
				} catch (UnreadableException e) {
					e.printStackTrace();
				}
			} else{
				block();
			}
		}
	}

	// Checks if the exploration worked out
	private class CheckExploration extends WakerBehaviour{
		private static final long serialVersionUID = -8591867333126826117L;
		private AcceptBids acceptBidsBehaviour;

		public CheckExploration(Agent a, long timeout, AcceptBids acceptBidsBehaviour) {
			super(a, timeout);
			this.acceptBidsBehaviour = acceptBidsBehaviour;
		}

		protected void onWake() {
			//After the timeout, stop accepting bids
			myAgent.removeBehaviour(acceptBidsBehaviour);
			//Build the new environment model from the obtained bids
			for (Bid bid:bids) {
				newEnvironmentModel.update(bid);
			}
			//If necessary (new model is empty) and possible (max time not reached), restart exploration
			if(newEnvironmentModel.isEmpty() && bidTime <= MaxBidTime) {
				bidTime = bidTime+BidTimeChange;
				myAgent.addBehaviour(new Exploration());
			} else { //Otherwise send what you have to the decision director
				bidTime = StartBidTime;
				myAgent.addBehaviour(new DecisionDirector("Exploration"));
			}
		}
	}

	//================================================================================
	// Planning/Scheduling
	//================================================================================
	private class Planning extends OneShotBehaviour {

		private static final long serialVersionUID = -7620693558974050017L;
		@Override
		public void action() {
			List<ResourceEvent> bestPath = getBestPath(getDesiredProperties(),environmentModel);
			//Create a new plan for the substring of the best path that needs to be scheduled
			PAPlan newPlanAttempt = new PAPlan(myAgent.getAID());
			int time = getCurrentTime()+planningWaitTime+nextExecutionStartTime ;
			int epsilon = 1000; // Allow small time changes in event duration
			int scheduleBound = Math.min(MaxScheduledEvents,bestPath.size());
			//Create the new plan based on the best path
			for (int i = 0; i< scheduleBound;i++){
				ResourceEvent scheduleEvent = bestPath.get(i);
				int eventEndTime = time+scheduleEvent.getEventTime()+epsilon;
				//Create a plan
				newPlanAttempt.addEvent(scheduleEvent, time, eventEndTime);
				time = eventEndTime;
			}
			//Remove any future events in the current plan
			removeScheduledEvents(getCurrentTime(),plan);
			//Schedule all of the events in the new plan
			ScheduleEvents scheduler = new ScheduleEvents(getCurrentTime(),newPlanAttempt);
			addBehaviour(scheduler);
		}
	}

	/** Finding the "best" (according to the weight transformer function) path
	 * @param environmentModel
	 * @param desiredProperties
	 * @return A list of Capabilities Edges that correspond to the best path
	 */
	private List<ResourceEvent> getBestPath(ArrayList<PhysicalProperty> desiredProperties, EnvironmentModel environmentModel){
		//Find the shortest path
		DijkstraShortestPath<ProductState, ResourceEvent> shortestPathGetter =
				new DijkstraShortestPath<ProductState, ResourceEvent>(environmentModel, this.getWeightFunction());
		shortestPathGetter.reset();

		//Set initial values
		int dist = 999999999; //a very large number
		ProductState desiredNodeFinal = null;

		//Find the desired distance with the shortest distance

		ArrayList<ProductState> desiredNodes = new ArrayList<ProductState>();

		//Find the desired nodes in the environment model
		for (PhysicalProperty property: desiredProperties){
			for (ProductState node : environmentModel.getVertices()){
				if (node.getPhysicalProperties().contains(property)){
					desiredNodes.add(node);
				}
			}
		}

		//Find the fastest path to one of the desired nodes
		for (ProductState desiredNode: desiredNodes){
			int compareDist = shortestPathGetter.getDistanceMap(environmentModel.getCurrentState()).get(desiredNode).intValue();
			if (compareDist < dist){
				dist = compareDist;
				desiredNodeFinal = desiredNode;
			}
		}

		return shortestPathGetter.getPath(environmentModel.getCurrentState(), desiredNodeFinal);
	}

	// The transformer for the capabilities of the resource agent to the desires of the product agent
	private Function<ResourceEvent, Integer> getWeightFunction(){
		return new Function<ResourceEvent,Integer>(){
			public Integer apply(ResourceEvent event) {
				return event.getEventTime();
			}
		};
	}

	// Remove the events after a certain time
	private boolean removeScheduledEvents(int time, PAPlan plan){
		int nextPlannedEventIndex = plan.getIndexOfNextEvent(time);
		ResourceEvent nextEvent = plan.getIndexEvent(nextPlannedEventIndex);
		boolean flag = true;
		//Remove any of the current plan's scheduled events s
		while(nextEvent!=null){
			RequestReschedule rescheduleRequest = new RequestReschedule(getAID(),
					plan.getIndexStartTime(nextPlannedEventIndex), plan.getIndexEndTime(nextPlannedEventIndex));
			sendMsg(ACLMessage.REQUEST, nextEvent.getEventAgent(), rescheduleRequest);

			//NEED CHECK IF THIS DOESN'T WORK TODO

			nextPlannedEventIndex+=1;
			nextEvent = plan.getIndexEvent(nextPlannedEventIndex);
		}
		return flag;
	}

	// Schedule events after a certain time
	private class ScheduleEvents extends OneShotBehaviour {
		private int time;
		private PAPlan plan;
		private AcceptReplys acceptor;
		public ScheduleEvents(int time, PAPlan plan) {
			this.time = time;
			this.plan = plan;
		}

		public boolean getResult() {
			return acceptor.getResult();
		}
		@Override
		public void action() {
			int nextPlannedEventIndex = plan.getIndexOfNextEvent(time);
			ResourceEvent nextEvent = plan.getIndexEvent(nextPlannedEventIndex);

			int numEvent = 0;
			int temp = nextPlannedEventIndex;
			while(plan.getIndexEvent(temp) !=null) {
				temp += 1;
				numEvent += 1;
			}

			acceptor = new AcceptReplys(numEvent);
			addBehaviour(acceptor);
			//Remove any of the current plan's scheduled events
			while (nextEvent != null) {

				RequestSchedule scheduleRequest = new RequestSchedule(getAID(), nextEvent,
						plan.getIndexStartTime(nextPlannedEventIndex), plan.getIndexEndTime(nextPlannedEventIndex));

				ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
				msg.addReceiver(nextEvent.getEventAgent());
				try {
					msg.setContentObject(scheduleRequest);
				} catch (IOException e) {
					e.printStackTrace();
				}
				//wait for RA's reply
				MessageTemplate template = MessageTemplate.and(
						MessageTemplate.MatchPerformative(ACLMessage.INFORM_REF),
						MessageTemplate.MatchConversationId(msg.getConversationId()));
				send(msg);
//				System.out.println(getLocalName() + " sends schedule request to " + nextEvent.getEventAgent().getLocalName() + ", " + getCurrentTime());

				nextPlannedEventIndex += 1;
				nextEvent = plan.getIndexEvent(nextPlannedEventIndex);
			}
			//check to see if all events were actually planned
			addBehaviour(new CheckPlan(myAgent,500, this, acceptor, plan));
		}
	}

	// Waits for bids from the RAs
	private class AcceptReplys extends CyclicBehaviour {
		private static final long serialVersionUID = 8202029907040536901L;
		private int num;
		private int yesReply;
		public AcceptReplys(int num) {
			this.num = num;
			this.yesReply = 0;
		}

		public boolean getResult() {
			return yesReply == num;
		}

		@Override
		public void action() {
			//Obtain any submitted bids and add them to the list
			MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.INFORM_REF);
			ACLMessage msg = myAgent.receive(mt);
			if(msg != null){
				try {
					Object msgObj = msg.getContentObject();
					if (msgObj instanceof RAReply) {
						RAReply reply = (RAReply) msg.getContentObject();
						if (reply.getFlag()) this.yesReply += 1;
						System.out.println(getLocalName() + " gets reply "+reply.getFlag()+", "+getCurrentTime());
					} else {
						putBack(msg);
					}
				} catch (UnreadableException e) {
					e.printStackTrace();
				}
			} else{
				block();
			}
		}
	}

	private class CheckPlan extends WakerBehaviour {
		private static final long serialVersionUID = 1618239324677520811L;
		private ScheduleEvents scheduler;
		private PAPlan newPlanAttempt;
		private AcceptReplys acceptor;
		public CheckPlan(Agent agent, int timeout, ScheduleEvents scheduler, AcceptReplys acceptor, PAPlan newPlanAttempt) {
			super(agent, timeout);
			this.scheduler = scheduler;
			this.newPlanAttempt = newPlanAttempt;
			this.acceptor = acceptor;
		}
		@Override
		protected void onWake() {
			if(!scheduler.getResult()){
				removeBehaviour(acceptor);
				removeScheduledEvents(getCurrentTime(),newPlanAttempt);
				newPlanAttempt = new PAPlan(myAgent.getAID());
				System.out.println(getLocalName() + " begins to replan " + ", " + getCurrentTime());
				addBehaviour(new Planning());
			} else {
				newPlan = newPlanAttempt;
				System.out.println(getLocalName() + " finds new plan: " + newPlan + ", " + getCurrentTime());
				addBehaviour(new DecisionDirector("Planning"));
			}
		}
	}



	/** Compare the product history to the production plan to obtain a desired set of physical states
	 * @return The desired set of properties for the product agent
	 */
	private ArrayList<PhysicalProperty> getDesiredProperties() {
		ArrayList<PhysicalProperty> incompleteProperties = new ArrayList<PhysicalProperty>();
		//Obtain the states that have occurred on the physical product using the product history
		ArrayList<ProductState> checkStates = new ArrayList<ProductState>();
		for (ResourceEvent event: this.productHistory.getOccurredEvents()){
			checkStates.add(event.getChild());
		}
		//Compare the production plan to the occurred states
		for(HashSet<PhysicalProperty> set:this.productionPlan.getSetList()){
			int highestIndex = -1; // the highest index when a desired property occurred
			//For each desired physical property
			for (PhysicalProperty desiredProperty : set){
				//Check if it has occurred
				boolean propertyComplete = false;
				for (int index = 0; index<checkStates.size();index++){
					if (checkStates.get(index).getPhysicalProperties().contains(desiredProperty)){
						//If it's occurred, overwrite the highest index, if appropriate
						if (index>highestIndex){
							highestIndex = index;
						}
						propertyComplete = true;
						break;
					}
				}
				//Add to incomplete properties if the property hasn't previously occurred in the product history
				if(!propertyComplete){
					incompleteProperties.add(desiredProperty);
				}
			}
			//If there are incomplete properties, then return these
			if (!incompleteProperties.isEmpty()){
				break;
			}
			//If there aren't incomplete properties, then go onto the next set of properties
			//Note: need to remove all of the properties that are associated with the previous set to continue
			else{
				for (int j=0;j<highestIndex;j++){
					checkStates.remove(0);
				}
			}
		}
		return incompleteProperties;
	}

	//================================================================================
	// Execute the next action by calling queryResource during the scheduled time
	//================================================================================
	public class Execution extends OneShotBehaviour{

		private static final long serialVersionUID = 4475886365215155940L;
		@Override
		public void action() {
			int nextIndex = plan.getIndexOfNextEvent((int) getCurrentTime());
			ResourceEvent nextAction = plan.getIndexEvent(nextIndex);
			System.out.println(getLocalName()+" is going to execute "+nextAction+", "+getCurrentTime());
			//Find the event time
			int nextEventTime = plan.getIndexStartTime(nextIndex);
			//Schedule querying the resource for the next action
			addBehaviour(new WaitForQuery(myAgent, nextEventTime - getCurrentTime(), nextAction));
		}
	}

	// Schedules a query in (timeout) time
	public class WaitForQuery extends WakerBehaviour{

		private static final long serialVersionUID = -3683057277678149213L;
		final ResourceEvent nextAction;

		public WaitForQuery(Agent a, long timeout, ResourceEvent nextAction) {
			super(a, timeout);
			this.nextAction = nextAction;
			lastActionQueriedTime = getCurrentTime();
			// Print it out if the PA is waiting too long
			if(timeout<0 || timeout>4000) {
				System.out.println("Next query for " + myAgent.getLocalName() + " is in " +timeout+ " milliseconds."+", "+getCurrentTime());
			}
		}

		protected void onWake (){
			//Set the queried edge
			RequestAction requestAction = new RequestAction(nextAction, myAgent.getAID());
			sendMsg(ACLMessage.REQUEST, nextAction.getEventAgent(), requestAction);
			myAgent.addBehaviour(new CheckForNoRaResponse(myAgent,
					requestAction.getQueriedEdge().getEventTime()+ActionTimeout,
					lastActionQueriedTime));
		}
	}

	public class CheckForNoRaResponse extends WakerBehaviour{

		private static final long serialVersionUID = -3683057277678149213L;
		final int checkActionQueriedTime;

		public CheckForNoRaResponse(Agent a, long timeout, int checkActionQueriedTime) {
			super(a, timeout);
			this.checkActionQueriedTime = checkActionQueriedTime;
		}

		protected void onWake (){
			if (checkActionQueriedTime==lastActionQueriedTime) {
//				System.out.println("No response from RA for " + myAgent.getLocalName());
//				exit();
			}
		}
	}

	private class AcceptSystemOutput extends CyclicBehaviour {

		private static final long serialVersionUID = 3662148034811593229L;

		@Override
		public void action() {
			MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.INFORM);
			ACLMessage msg = myAgent.receive(mt);
			if(msg != null){
				try {
					Object msgObj = msg.getContentObject();

					if (msgObj instanceof SystemOutput) {
						System.out.println(getLocalName() + " receives RA's output about event finished"+", "+getCurrentTime());
						SystemOutput systemOutput = (SystemOutput) msg.getContentObject();

						//Update the models on the latest information about the product state
						environmentModel.update(systemOutput.getCurrentState());
						productHistory.update(systemOutput.getGraph(), systemOutput.getCurrentState(), systemOutput.getOccuredEvents());

						//If there is no next action, find a new plan by telling the Decision Director that Execution has been finished
						if (plan.isEmpty(getCurrentTime())){
							myAgent.addBehaviour(new DecisionDirector("Execution"));
						} else{
							//Start the next step of execution
							addBehaviour(new Execution());
						}
					} else {
						putBack(msg);
					}
				} catch (UnreadableException e) {
					myAgent.doDelete(); e.printStackTrace();
				}
			} else {
				block();
			}
		}
	}

	//================================================================================
	// Helper methods
	//================================================================================

	void sendMsg(int type, AID receiver, Serializable content) {
		ACLMessage msg = new ACLMessage(type);
		msg.addReceiver(receiver);
		try {msg.setContentObject(content);}
		catch (IOException e) {e.printStackTrace();}
		send(msg);
	}

	protected int getCurrentTime() {
		return (int) (System.currentTimeMillis() % Integer.MAX_VALUE);
	}
}