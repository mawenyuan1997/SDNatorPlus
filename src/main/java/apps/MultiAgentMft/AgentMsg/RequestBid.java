package apps.MultiAgentMft.AgentMsg;

import apps.MultiAgentMft.AgentInfo.Bid;
import apps.MultiAgentMft.AgentInfo.PhysicalProperty;
import apps.MultiAgentMft.AgentInfo.ProductState;
import jade.core.AID;

import java.io.Serializable;

public class RequestBid implements Serializable{

	private static final long serialVersionUID = -6898767517666161984L;
	private AID productAgent;
	private PhysicalProperty desiredProperty;
	private ProductState currentNode;
	private int maxTime;
	private Bid bid;
	private int existingBidTime;

	public RequestBid(AID productAgent, PhysicalProperty desiredProperty, ProductState currentNode,
                      int maxTime, Bid bid, int existingBidTime) {
		this.productAgent = productAgent;
		this.desiredProperty =  desiredProperty;
		this.currentNode = currentNode;
		this.maxTime = maxTime;
		this.bid = bid;
		this.existingBidTime = existingBidTime;
	}

	public ProductState getCurrentNode() {
		return currentNode;
	}

	public void setCurrentNode(ProductState currentNode) {
		this.currentNode = currentNode;
	}

	public Bid getBid() {
		return bid;
	}

	public void setBid(Bid bid) {
		this.bid = bid;
	}

	public int getExistingBidTime() {
		return existingBidTime;
	}

	public void setExistingBidTime(int existingBidTime) {
		this.existingBidTime = existingBidTime;
	}

	public AID getProductAgent() {
		return productAgent;
	}

	public PhysicalProperty getDesiredProperty() {
		return desiredProperty;
	}

	public int getMaxTime() {
		return maxTime;
	}

}