package test;


import apps.Coordinator;
import apps.MultiAgentMft.AgentInfo.*;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.wrapper.AgentController;
import jade.wrapper.StaleProxyException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import utils.Group;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;

public class AgentLauncher extends Agent {

    protected void setup() {
        addBehaviour(new TestFullyDistributed());

    }

    private class TestFullyDistributed extends OneShotBehaviour {
        @Override
        public void action() {
            HashSet<String> g = new HashSet<>();
            HashMap<AID, Capabilities> raCapabilities = new HashMap<>();
            HashMap<String, ProductState> states = new HashMap<>();  // map from product state id to the object

            // read xml file
            ClassLoader classLoader = getClass().getClassLoader();
            File file = new File(classLoader.getResource("RAsetting").getFile());
            DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory
                    .newInstance();
            DocumentBuilder documentBuilder = null;
            Document document = null;
            try {
                documentBuilder = documentBuilderFactory.newDocumentBuilder();
                document = documentBuilder.parse(file);
            } catch (ParserConfigurationException e) {
                e.printStackTrace();
            } catch (SAXException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

            // read all product states(nodes) from xml input
            NodeList nList = document.getElementsByTagName("ProductState");
            int nLength = nList.getLength();
            for(int i = 0; i < nLength; i ++) {
                Node node = nList.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    Element element = (Element) node;
                    String object = element.getElementsByTagName("objectname").item(0).getTextContent();
                    String process = element.getElementsByTagName("process").item(0).getTextContent();
                    int x = Integer.parseInt(element.getElementsByTagName("x").item(0).getTextContent());
                    int y = Integer.parseInt(element.getElementsByTagName("y").item(0).getTextContent());
                    ProductState ps = new ProductState(object, new PhysicalProperty(process), new PhysicalProperty(new Point(x, y)));
                    String id = element.getAttribute("id");
                    states.put(id, ps);
                }
            }

            // read all resource events(edges) from xml input
            nList = document.getElementsByTagName("ResourceEvent");
            nLength = nList.getLength();
            for(int i = 0; i < nLength; i ++) {
                Node node = nList.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    Element element = (Element) node;
                    AID raId = new AID(element.getElementsByTagName("raId").item(0).getTextContent(), AID.ISLOCALNAME);
                    String from = element.getElementsByTagName("from").item(0).getTextContent();
                    String to = element.getElementsByTagName("to").item(0).getTextContent();
                    String activeMethod = element.getElementsByTagName("activeMethod").item(0).getTextContent();
                    int eventTime = Integer.parseInt(element.getElementsByTagName("eventTime").item(0).getTextContent());
                    ResourceEvent re = new ResourceEvent(raId, states.get(from), states.get(to), activeMethod, eventTime);
                    if (!raCapabilities.containsKey(raId)) raCapabilities.put(raId, new Capabilities());
                    raCapabilities.get(raId).addEdge(re);
                }
            }

            // Populate RA Neighbors
            HashMap<AID, HashMap<AID, ProductState>> raNeighborTable = new HashMap<>();
            for(AID i : raCapabilities.keySet()) {
                HashMap<AID, ProductState> table = new HashMap<AID, ProductState>();
                for (AID j : raCapabilities.keySet()) {
                    if (i != j) {
                        for (ProductState state : raCapabilities.get(i).getVertices()) {
                            if (raCapabilities.get(j).containsVertex(state)) {   // RA j shares state with RA i
                                table.put(j, state);
                                break;
                            }
                        }
                    }
                }
                raNeighborTable.put(i, table);
            }

            // Creating resource agents
            for (AID aid : raCapabilities.keySet()) {
                //Create the agent
                try {
                    AgentController ac;
                    ac = getContainerController().createNewAgent(
                            aid.getLocalName(),
                            "apps.MultiAgentMft.IndepResourceAgent",//"hybridSDNator.Conveyor",
                            new Object[] {raCapabilities.get(aid), raNeighborTable.get(aid)});

                    ac.start();
                } catch (StaleProxyException e) {
                    e.printStackTrace();
                }
            }
            //Wait a couple of seconds and initialize a test product agent
            myAgent.doWait(500);

            createPA("ProductAgent1", states.get("1"), new AID("0", AID.ISLOCALNAME), states.get("9"));
            createPA("ProductAgent2", states.get("1"), new AID("0", AID.ISLOCALNAME), states.get("7"));

            int startTime = (int) (System.currentTimeMillis() % Integer.MAX_VALUE);
            addBehaviour(new ReceiveExitTime(2, startTime));

        }

        private void createPA(String name, ProductState origin, AID startResource, ProductState destination) {
            ProductionPlan pp = new ProductionPlan();
            pp.add(destination.getPhysicalProperties().get(0));
            AgentController ac;
            try {
                ac = getContainerController().createNewAgent(
                            name,
                            "apps.MultiAgentMft.ProductAgent",
                            new Object[] {startResource,
                                            origin,
                                            pp
                            });
                ac.start();
            } catch (StaleProxyException e) { e.printStackTrace();}
        }
    }

    private class ReceiveExitTime extends CyclicBehaviour {
        private int num, startTime;
        private ArrayList<Integer> exitTime;

        public ReceiveExitTime(int num, int startTime) {
            this.num = num;
            this.startTime = startTime;
            this.exitTime = new ArrayList<>();
        }

        @Override
        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.INFORM);
            ACLMessage msg = myAgent.receive(mt);
            if(msg != null){
                exitTime.add(Integer.parseInt(msg.getContent()));
                if (exitTime.size() == num) {
                    System.out.println("total time:"+(Collections.max(exitTime)-startTime));
                    myAgent.doDelete();
                }
            } else{
                block();
            }
        }
    }

    private class TestModeSwitch extends OneShotBehaviour {
        @Override
        public void action() {
            HashSet<String> g = new HashSet<>();
            g.add("app0");g.add("app1");g.add("app2");g.add("app3");
            HashSet<Group> groups = new HashSet<>();
            groups.add(new Group(g));
            Coordinator coord = new Coordinator(groups);
            coord.start();
            for(int i=0; i<4; i++) {
                try {
                    AgentController ac = getContainerController().createNewAgent(
                            "app"+i,
                            "apps.MultiAgentMft.IndepResourceAgent",
                            new Object[]{"app"+i});
                    ac.start();
                } catch (StaleProxyException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
