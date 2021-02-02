package apps.MultiAgentMft;

import apps.MultiAgentMft.AgentInfo.*;
import apps.MultiAgentMft.AgentMsg.*;
import due.DUE;
import edu.uci.ics.jung.algorithms.shortestpath.DijkstraShortestPath;
import edu.uci.ics.jung.graph.DirectedSparseGraph;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.UnreadableException;
import jade.wrapper.AgentController;
import jade.wrapper.StaleProxyException;
import messages.SwitchRequest;
import messages.SwitchResult;
import messages.Vote;
import messages.VoteRequest;
import redis.clients.jedis.JedisPubSub;
import utils.*;

import java.io.IOException;
import java.io.Serializable;
import java.util.*;


public class IndepResourceAgent extends Agent {

	private String appId;
	private DUE due;

	private Capabilities resourceCapabilities;
	private HashMap<AID, ProductState> tableNeighborNode;
	private Set<AID> neighbors;
	private RASchedule RAschedule;

	private MachineSimulator machine;
	private HashMap<ProductState,HashMap<AID,ResourceEvent>> notifyAgentWhenState;

	@Override
	protected void setup(){
		Object[] args = getArguments();
		assert args.length == 2 && args[0] instanceof Capabilities
				&& args[1] instanceof HashMap;

		// set fields
		appId = getLocalName();
		resourceCapabilities = (Capabilities) args[0];
		tableNeighborNode = (HashMap<AID, ProductState>) args[1];
		neighbors = tableNeighborNode.keySet();
		notifyAgentWhenState = new HashMap<>();
		RAschedule = new RASchedule(this);
		machine = new MachineSimulator();

		System.out.println(appId + " setup ");

		// setup DUE
		due = new DUE(appId);
		JedisPubSub jedisPubSub = new RAPubSubHandler();
		due.getSubscribeApi().observe(jedisPubSub, MsgChannelFormat.getAppChannel(appId));

//		if (appId.equals("app3")) {
//			due.requestSwitch(RunningMode.Dependent, appId);
//
//		}
		// add agent behavior
		for(ResourceEvent e : resourceCapabilities.getEdges()) {
			addBehaviour(new MachineMonitoring(this, 100, e));
		}
		addBehaviour(new AgentCooperation());
	}


	private class RAPubSubHandler extends JedisPubSub {
		@Override
		public void onPMessage(String pattern, String channel, String msg) {
			String[] splited = channel.split("\\.");
			if (splited[0].equals("apps") && splited[1].equals(appId)) {
				Object msgObj = SerializationUtility.fromString(msg);

				System.out.println(appId + " receives "+msgObj);

				if (msgObj instanceof VoteRequest) {
					RunningMode mode = ((VoteRequest) msgObj).getRequestMode();

					// vote true or false
					due.vote(true, appId);
				} else if (msgObj instanceof SwitchResult) {
					if (((SwitchResult) msgObj).isSwitchSuccess()) {
						System.out.println(appId + " switch mode");
					}
				}
			}
		}

		@Override
		public void onPSubscribe(String channel, int subscribedChannels) {
			System.out.println(" subscribes to channel : "+ channel);
		}
	}
	// ======================================================================
	// Distributed mode communication
	// ======================================================================
	private class AgentCooperation extends CyclicBehaviour {
		@Override
		public void action() {
			MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.REQUEST);
			ACLMessage msg = myAgent.receive(mt);
			if (msg != null) {
				try {
					Object msgObj = msg.getContentObject();
					if (msgObj instanceof RequestAction) {
						System.out.println(getLocalName()+" receives RequestAction from "
								+msg.getSender().getLocalName()+", "+getCurrentTime());
						query((RequestAction) msg.getContentObject());
					} else if (msgObj instanceof RequestBid) {
						System.out.println(getLocalName()+" receives RequestBid"+", "+getCurrentTime());
						teamQuery((RequestBid) msg.getContentObject(),msg.getSender());
					} else if (msgObj instanceof RequestSchedule) {
						System.out.println(getLocalName()+" receives RequestSchedule"+", "+getCurrentTime());
						boolean response = requestScheduleTime((RequestSchedule) msg.getContentObject());
						ACLMessage replyMsg = msg.createReply();
						replyMsg.setPerformative( ACLMessage.INFORM_REF );
						try { replyMsg.setContentObject(new RAReply(response));}
						catch (IOException e) {e.printStackTrace();}
						send(replyMsg);
					} else if (msgObj instanceof RequestReschedule) {
						System.out.println(getLocalName()+" receives RequestReschedule"+", "+getCurrentTime());
						removeScheduleTime((RequestReschedule) msg.getContentObject());
					}else {
						putBack(msg);
					}
				} catch (UnreadableException e) {
					e.printStackTrace();
				}

			}
		}
	}

	// handle action request
	public boolean query(RequestAction action) {
		ResourceEvent queriedEdge = action.getQueriedEdge();
		AID productAgent = action.getProductAgent();
		//Find the desired edge
		ResourceEvent desiredEdge = null;
		for (ResourceEvent edge : resourceCapabilities.getEdges()){
			if (edge.getActiveMethod().equals(queriedEdge.getActiveMethod())){
				desiredEdge = edge;
				break;
			}
		}
		//Find the offset between the queried edge and when the actual program should be run
		int edgeOffset = queriedEdge.getEventTime() - resourceCapabilities.findEdge(queriedEdge.getParent(),queriedEdge.getChild()).getEventTime();
		int startTime = getCurrentTime()+edgeOffset;
		//If the product agent is scheduled for this time, run the desired program at that time;
		if (desiredEdge!=null &&  this.RAschedule.checkPATime(productAgent, startTime, startTime+desiredEdge.getEventTime())){
			//Schedule it for the future
			System.out.println("send signal for "+action);
			addBehaviour(new SendSignal(this, edgeOffset, desiredEdge, productAgent));
			HashMap<AID,ResourceEvent> edgeProductMap = new HashMap<AID,ResourceEvent>();
			edgeProductMap.put(productAgent, desiredEdge);
			this.notifyAgentWhenState.put(desiredEdge.getChild(), edgeProductMap);
			return true;
		}
		return false;
	}

	// ======================================================================
	// Send signal to machine for execution
	// ======================================================================
	public class SendSignal extends WakerBehaviour {
		private static final long serialVersionUID = 6800760293776896340L;
		private final ResourceEvent desiredEdge;
		private final AID productAgent;

		public SendSignal(Agent a, long timeout, ResourceEvent desiredEdge, AID productAgent) {
			super(a, timeout);
			this.desiredEdge = desiredEdge;
			this.productAgent = productAgent;
		}

		protected void onWake() {
			runEdge(desiredEdge,productAgent);
		}
	}

	public void runEdge(ResourceEvent edge, AID productAgent) {
		machine.set(edge);
		System.out.println(productAgent.getLocalName()+" in Execution, time " + getCurrentTime());
		System.out.println(productAgent.getLocalName()+" in state "+edge.getChild());
	}

	// ======================================================================
	// Check the machine to see if job completes
	// ======================================================================
	private class MachineMonitoring extends TickerBehaviour{
		private final ProductState productState;
		private final ResourceEvent edge;
		public MachineMonitoring(Agent a, Integer monitorPeriod, ResourceEvent event) {
			super(a, monitorPeriod);
			this.edge = event;
			this.productState = event.getChild();
		}
		@Override
		protected void onTick() {
			boolean varValue = machine.read(this.edge);
			if (varValue) {
				System.out.println(getLocalName() + " done execution"+", "+getCurrentTime());
				System.out.println(productState);
			}
			if(varValue && notifyAgentWhenState.containsKey(productState)) {
				//Message to inform the PA about the product state
				System.out.println(getLocalName()+" inform PA"+", "+getCurrentTime());
				for(AID productAgent:notifyAgentWhenState.get(productState).keySet()) {
					informPA(productAgent, notifyAgentWhenState.get(productState).get(productAgent));
					notifyAgentWhenState.remove(productState);
				}
			}
		}
	}

	// inform PA about an event finished
	public void informPA(AID productAgent, ResourceEvent edge){
		DirectedSparseGraph<ProductState, ResourceEvent> outputGraph = new DirectedSparseGraph<ProductState, ResourceEvent>();
		outputGraph.addEdge(edge, edge.getParent(),edge.getChild());
		ArrayList<ResourceEvent> occuredEvents = new ArrayList<ResourceEvent>();
		occuredEvents.add(edge);
		SystemOutput systemOutput = new SystemOutput(outputGraph, edge.getChild(), occuredEvents);
		sendMsg(ACLMessage.INFORM, productAgent, systemOutput);
	}

	// handle Bid request
	private void teamQuery(RequestBid bidRequest, AID requestor) {
		//Deep copy everything
		Bid currentBid = bidRequest.getBid();
		ProductState currentState = bidRequest.getCurrentNode().copy();
		PhysicalProperty desiredProperty = bidRequest.getDesiredProperty();
		int existingTime = bidRequest.getExistingBidTime();
		int maxTime = bidRequest.getMaxTime();
		AID productAgent = bidRequest.getProductAgent();
		System.out.println(getLocalName()+" receive from "+requestor.getLocalName()+" bid request "+bidRequest);
		//need to update based on current schedule
		DirectedSparseGraph<ProductState,ResourceEvent> updatedCapabilities = copyGraph(resourceCapabilities);
		//Copy graphs to not mess with pointers
		Bid bid = currentBid.copyBid();
		DirectedSparseGraph<ProductState,ResourceEvent> searchGraph = currentBid.copyBid();
		// 1. Update events in capabilities based on current schedule
		// 2. Create new full graph (capabilities + bid)
		Iterator<ResourceEvent> itr = updatedCapabilities.getEdges().iterator();
		while (itr.hasNext()){
			// Find the edge and update it based on current schedule
			ResourceEvent edge = itr.next().copy();
			int bidOffset = RAschedule.getNextFreeTime(existingTime,edge.getEventTime())-existingTime;
			if (bidOffset != 0) {
				bidOffset+=1000;
			}
			edge.setWeight(edge.getEventTime()+bidOffset);
			//Add to entire graph
			searchGraph.addEdge(edge, edge.getParent(), edge.getChild());
		}
		DijkstraShortestPath<ProductState, ResourceEvent> shortestPathGetter =
				new DijkstraShortestPath<ProductState, ResourceEvent>(searchGraph);
		shortestPathGetter.reset();
		//Check if a node in the capabilities graph satisfies a desired property
		boolean flag = false;
		ProductState desiredVertex = null;
		for (ProductState vertex : updatedCapabilities.getVertices()){
			if(vertex.getPhysicalProperties().contains(desiredProperty)){
				flag = true;
				desiredVertex = vertex;
				break;
			}
		}
		int currentTime = getCurrentTime();
		System.out.println(getLocalName()+" flag found vertex:"+flag);
		// If a vertex satisfied a desired property
		if (flag){
			//Find the shortest path
			shortestPathGetter.reset();
			List<ResourceEvent> shortestPathCandidateList = shortestPathGetter.getPath(currentState, desiredVertex);

			//Check if there is a path from the current node to the desired one
			if (!shortestPathCandidateList.isEmpty()) {
				//Calculate the bid
				System.out.println(getLocalName()+" path not empty:"+shortestPathCandidateList);
				int bidTime = existingTime;
				for (ResourceEvent path : shortestPathCandidateList){
					bidTime = bidTime + path.getEventTime();
					bid.addEdge(path, path.getParent(), path.getChild());
					bidRequest.setCurrentNode(path.getChild());
				}
				//Submit the bid to the product agent
				if (bidTime < currentTime + maxTime) {
					ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
					msg.addReceiver(productAgent);
					try {msg.setContentObject(bid);}
					catch (IOException e) {e.printStackTrace();}
					send(msg);
				}
			}
		}
		//Push the bid negotiation to a neighbor
		else{ //Note: if a desired node is found, don't push it to the neighbor (can be turned off)
			for (AID neighbor: neighbors){
				if (!neighbor.equals(requestor)) {
					ProductState neighborNode = tableNeighborNode.get(neighbor);
					//Find the shortest path
					shortestPathGetter.reset();
					List<ResourceEvent> shortestPathCandidateList = new LinkedList<ResourceEvent>();
					if(searchGraph.containsVertex(currentState)) {
						shortestPathCandidateList = shortestPathGetter.getPath(currentState, neighborNode);
					}
					else {
						break;
					}
					//If the current state is the 1st neighbor state -> pass the bid on to the resource's neighbor
					if(shortestPathCandidateList.isEmpty() && bid.getEdgeCount()==0){
						RequestBid newBidRequest = new RequestBid(productAgent, desiredProperty, currentState,
								maxTime, bid, existingTime);
						sendMsg(ACLMessage.REQUEST, neighbor, newBidRequest);
						System.out.println(getLocalName()+" sends bid to "+neighbor.getLocalName());
					}
					else
						//Don't revisit the same edges
						if(!bid.getEdges().containsAll(shortestPathCandidateList)){
							//Calculate the bid
							int bidTime = existingTime; //Reset bid time
							for (ResourceEvent path : shortestPathCandidateList){
								bidTime = bidTime + path.getEventTime();
								bid.addEdge(path, path.getParent(), path.getChild());
							}
							ProductState newBidPartState = shortestPathCandidateList.get(shortestPathCandidateList.size()-1).getChild();
							// Request bids from the neighbors
							if (bidTime < currentTime + maxTime && bidTime >= existingTime){
								RequestBid newBidRequest = new RequestBid(productAgent, desiredProperty,
										newBidPartState, maxTime, bid, bidTime);
								sendMsg(ACLMessage.REQUEST, neighbor, newBidRequest);
								System.out.println(getLocalName()+" sends bid to "+neighbor.getLocalName());
							}
						}
				}
			}
		}
	}

	// handle schedule request
	public boolean requestScheduleTime(RequestSchedule requestSchedule) {
		ResourceEvent edge = requestSchedule.getEdge();
		AID productAgent = requestSchedule.getProductAgent();
		int startTime = requestSchedule.getStartTime();
		int endTime = requestSchedule.getEndTime();
		int edgeOffset = edge.getEventTime() - resourceCapabilities.findEdge(edge.getParent(),edge.getChild()).getEventTime();
		boolean planned = this.RAschedule.addPA(productAgent, startTime+edgeOffset, endTime, false);
		System.out.println(productAgent.getLocalName() + ",Scheduled," + getCurrentTime());
		return planned;
	}

	// handle reschedule request
	public boolean removeScheduleTime(RequestReschedule requestReschedule) {
		AID productAgent = requestReschedule.getProductAgent();
		int startTime = requestReschedule.getStartTime();
		int endTime = requestReschedule.getEndTime();
		boolean res = this.RAschedule.removePA(productAgent, startTime, endTime);
		return res;
	}

	protected void takeDown() {
		// Printout a dismissal message
		System.out.println(getAID().getName()+" terminating.");
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

	public DirectedSparseGraph<ProductState, ResourceEvent> copyGraph(
			DirectedSparseGraph<ProductState, ResourceEvent> oldgraph) {
		DirectedSparseGraph<ProductState, ResourceEvent> graph = new DirectedSparseGraph<ProductState, ResourceEvent>();
		for (ResourceEvent e : oldgraph.getEdges()){
			ResourceEvent newEdge = e.copy();
			graph.addEdge(newEdge,newEdge.getParent(),newEdge.getChild());
		}
		return graph;
	}

	protected int getCurrentTime() {
		return (int) (System.currentTimeMillis() % Integer.MAX_VALUE);
	}

}