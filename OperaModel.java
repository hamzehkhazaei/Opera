package opera;

import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import opera.KalmanFilter.ModelParameter;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import opera.Core.LQM;
import opera.KalmanFilter.EstimationResults;
import opera.KalmanFilter.KalmanConfiguration;
import opera.KalmanFilter.KalmanEstimator;
import util.MeasuresUtil;

/**
 * @author marin
 */
public class OperaModel {
    LQM model = null;
    Document doc = null;
    Document results = null;

    XPath m_xPath = XPathFactory.newInstance().newXPath();

    PrintWriter writer = null;

    private final String MODEL_THINK_TIME = "/Model/Workloads/ThinkTimes/ThinkTime/@time";
    private final String MODEL_THINK_TIME_SCENARIO = "/Model/Workloads/ThinkTimes/ThinkTime[@scenario='%s']/@time";

    private final String MODEL_POPULATION = "/Model/Workloads/Users/text()";
    private final String MODEL_POPULATION_SCENARIO = "/Model/Workloads/WorkloadMixes[%d]/Mix[@scenario='%s']/@load";

    private final String MODEL_SCENARIO_DEMAND_CPU = "/Model/Scenarios/Scenario[@name='%s']/Call[@callee='%s']/Demand/@CPUDemand";
    private final String MODEL_SCENARIO_DEMAND_DISK = "/Model/Scenarios/Scenario[@name='%s']/Call[@callee='%s']/Demand/@DiskDemand";

    private final String MODEL_CONTAINER_THREADS = "/Model/Topology/Cluster/Container[@name='%s']/@parallelism";

    private final String MODEL_NODE_CPU_MULTIPLICITY = "/Model/Topology/Node[@name='%s']/@CPUParallelism";
    private final String MODEL_NODE_DISK_MULTIPLICITY = "/Model/Topology/Node[@name='%s']/@DiskParallelism";

    private final String RESULTS_SERVICE_ALLOCATION = "/Results/Architecture/Service[@name='%s']/@allocatedToContainer";

    private final String RESULTS_RESPONSE_TIME_SCENARIO = "/Results/Architecture/Workloads[%d]/Scenario[@name='%s']/ResponseTime";
    private final String RESULTS_RESPONSE_TIME_CONTAINER = "/Results/Architecture/Workloads[%d]/Container[@name='%s']/Scenario[@scenarioName='%s']/@responseTime";
    private final String RESULTS_RESPONSE_TIME_SERVICE = "/Results/Architecture/Workloads[%d]/Service[@name='%s']/Scenario[@name='%s']/@responseTime";

    private final String RESULTS_UTILIZATION_CONTAINER = "/Results/Architecture/Workloads[%d]/Container[@name='%s']/@Utilization";
    private final String RESULTS_UTILIZATION_SERVICE = "/Results/Architecture/Workloads[%d]/Service[@name='%s']/@Utilization";
    private final String RESULTS_UTILIZATION_NODE = "/Results/Architecture/Workloads[%d]/Node[@name='%s']/%s/Utilization";

    private final String RESULTS_THROUGHPUT = "/Results/Architecture/Workloads[%d]/Scenario[@name='%s']/Throughput";

    DecimalFormat formatter = new DecimalFormat("#0.00000");

    public OperaModel() throws FileNotFoundException, UnsupportedEncodingException {
        super();
        writer = new PrintWriter("./output/modelParametersFinal.csv", "UTF-8");
        writer.println("CPUDem_Proxy" + "," + "CPUDem_LB" + "," + "CPUDem_Web" + "," + "CPUDem_Analytic" + "," + "CPUDem_Db");
    }

    public void setModel(String pxlFile) {
        model = new LQM();
        doc = model.parsePXL(pxlFile);
    }

    public void setModelFromString(String pxlModel) {
        model = new LQM();
        doc = model.parsePxlFromString(pxlModel);
    }


    /**
     * Sets the total population. The total population is not related to any
     * mix.
     * <p>
     * This method does not change the mixes in any way.
     *
     * @param value the new value for the total population.
     */
    public void SetPopulation(int value) {
        this.SetXPathModelNodesValue(MODEL_POPULATION, String.valueOf(value));
    }

    /**
     * Sets the population for a scenario to the specified value. Only the first
     * mix will be affected. Calls <code>SetPopulation(int, String, int)</code>
     * passing 0 for the first parameter.
     * <p>
     * This method does not update the total population.
     *
     * @param scenarioName the name of the scenario for which to change the population.
     * @param value        the new population for the scenario.
     */
    public void SetPopulation(String scenarioName, double value) {
        this.SetPopulation(0, scenarioName, value);
    }

    /**
     * Sets the population for a scenario to the specified value.
     * <p>
     * This method does not update the total population.
     *
     * @param mixNo        the mix index that will be affected by the change (the indext
     *                     starts at 0).
     * @param scenarioName the name of the scenario for which to change the population.
     * @param value        the new population for the scenario.
     */
    public void SetPopulation(int mixNo, String scenarioName, double value) {
        String sXPathExpression = String.format(MODEL_POPULATION_SCENARIO, mixNo + 1, scenarioName);
        this.SetXPathModelNodesValue(sXPathExpression, String.valueOf(value));
    }

    /**
     * Set the think time for all scenarios to the specified value.
     *
     * @param value the new think time.
     */
    public void SetThinkTime(double value) {
        this.SetXPathModelNodesValue(MODEL_THINK_TIME, String.valueOf(value));
    }

    /**
     * The the think time for the first <code>values.length</code>, in the order
     * they were defined.
     * <p>
     * If there are more values specified than scenarios defined, the extra
     * values will be ignored.
     *
     * @param values an array with the new think times.
     */
    public void SetThinkTime(double[] values) {
        // get the defined scenarios in this model
        String[] scenarioNames = this.GetScenarioNames();

        // set values for this scenarios in the defined order
        for (int i = 0; i < values.length && i < scenarioNames.length; ++i) {
            this.SetThinkTime(scenarioNames[i], values[i]);
        }
    }

    /**
     * Set the think time for a single scenario.
     *
     * @param scenarioName the scenario for which the think time must be modified.
     * @param value        the new think time for the scenario.
     */
    public void SetThinkTime(String scenarioName, double value) {
        String sXPathExpression = String.format(MODEL_THINK_TIME_SCENARIO, scenarioName);
        this.SetXPathModelNodesValue(sXPathExpression, String.valueOf(value));
    }

    /**
     * Set the think time for an array of scenario. This method will call
     * <code>SetThinkTime(String, double)</code> for every scenario from the
     * array.
     * <p>
     * If the array sizes don't match, the behaviour is undefined.
     *
     * @param scenarioNames an array with scenario names for which to change the think
     *                      times.
     * @param values        an array with the new think times. This array must have the
     *                      same size as <code>scenarioNames</code>.
     */
    public void SetThinkTime(String[] scenarioNames, double[] values) {
        for (int i = 0; i < scenarioNames.length; ++i) {
            this.SetThinkTime(scenarioNames[i], values[i]);
        }
    }

    /**
     * Set the CPU demand for a host when the specified service is executed, as
     * part of a scenario.
     * <p>
     * A scenario is a sequence of service calls, that are executed on hosts.
     * Each call requires CPU. The demand is the time necessary for the host CPU
     * to execute a single call.
     *
     * @param scenarioName
     * @param serviceName
     * @param value
     */
    public void SetCpuDemand(String scenarioName, String serviceName, double value) {
        String sXPathExpression = String.format(MODEL_SCENARIO_DEMAND_CPU, scenarioName, serviceName);
        this.SetXPathModelNodesValue(sXPathExpression, String.valueOf(value));
    }

    /**
     * Set the DISK demand for a host when the specified service is executed, as
     * part of a scenario.
     * <p>
     * A scenario is a sequence of service calls, that are executed on hosts.
     * Each call requires DISK. The demand is the time necessary for the host
     * DISK to execute a single call.
     *
     * @param scenarioName
     * @param serviceName
     * @param value
     */
    public void SetDiskDemand(String scenarioName, String serviceName, double value) {
        String sXPathExpression = String.format(MODEL_SCENARIO_DEMAND_DISK, scenarioName, serviceName);
        this.SetXPathModelNodesValue(sXPathExpression, String.valueOf(value));
    }

    /**
     * Sets the number of threads in a container.
     *
     * @param containerName the container for which to change the number of threads.
     * @param value         the new number of threads.
     */
    public void SetContainerThreads(String containerName, int value) {
        String sXPathExpression = String.format(MODEL_CONTAINER_THREADS, containerName);
        this.SetXPathModelNodesValue(sXPathExpression, String.valueOf(value));
    }

    /**
     * Sets the multiplicity of a node (i.e. the number of nodes of this type).
     * This method assumes that a node has exactly one CPU and exactly one DISK,
     * so changing multiplicity of the node means changing multiplicity of CPU
     * and DISK for the node to the same value.
     *
     * @param nodeName the node for which to change the multiplicity.
     * @param value    the new number of nodes.
     */
    public void SetNodeMultiplicity(String nodeName, int value) {
        String sXPathExpression = String.format(MODEL_NODE_CPU_MULTIPLICITY, nodeName);
        this.SetXPathModelNodesValue(sXPathExpression, String.valueOf(value));

        sXPathExpression = String.format(MODEL_NODE_DISK_MULTIPLICITY, nodeName);
        this.SetXPathModelNodesValue(sXPathExpression, String.valueOf(value));
    }

    /**
     * Updates <b>all</b> elements, from the mdoel, that match the specified
     * XPath expression. This is a generic method to change model values that
     * don't have a dedicated function to do so.
     *
     * @param sXPathExpression
     * @param sValue
     */
    public void SetXPathModelNodesValue(String sXPathExpression, String sValue) {
        try {
            NodeList nodes = (NodeList) this.m_xPath.evaluate(sXPathExpression, this.doc, XPathConstants.NODESET);
            for (int i = 0; i < nodes.getLength(); ++i) {
                nodes.item(i).setNodeValue(sValue);
            }
        } catch (Exception e) {
        }
    }

    /**
     * Get the names of the scenarios defined in this model.
     *
     * @return An array containing the names, or <code>null</code> if an
     * exception occurred.
     */
    public String[] GetScenarioNames() {
        String sXPathExpression = "/Model/Scenarios/Scenario/@name";
        return this.GetXPathArrayString(this.doc, sXPathExpression);
    }

    /**
     * Get the names of the nodes defined in this model.
     *
     * @return An array containing the names, or <code>null</code> if an
     * exception occurred.
     */
    public String[] GetNodeNames() {
        String sXPathExpression = "/Model/Topology/Node/@name";
        return this.GetXPathArrayString(this.doc, sXPathExpression);
    }

    /**
     * Get the names of the services defined in this model.
     *
     * @return An array containing the names, or <code>null</code> if an
     * exception occurred.
     */
    public String[] GetServiceNames() {
        String sXPathExpression = "/Model/Scenarios/Services/Service/@name";
        return this.GetXPathArrayString(this.doc, sXPathExpression);
    }

    /**
     * Get the container to which the specified service is allocated. The model
     * must be solved first.
     *
     * @param serviceName The service that must be queried.
     * @return The name of the container to which this service is allocated.
     */
    public String GetServiceAllocation(String serviceName) {
        String sXPathExpression = String.format(RESULTS_SERVICE_ALLOCATION, serviceName);
        return this.GetXPathValueString(this.results, sXPathExpression);
    }

    /**
     * Get the CPU demand when a specified service is executed, as part of a
     * scenario.
     *
     * @param scenarioName
     * @param serviceName
     * @return
     */
    public double GetCpuDemand(String scenarioName, String serviceName) {
        String sXPathExpression = String.format(MODEL_SCENARIO_DEMAND_CPU, scenarioName, serviceName);
        return this.GetXPathModelDouble(sXPathExpression);
    }

    /**
     * Get the DISK demand when a specified service is executed, as part of a
     * scenario.
     *
     * @param scenarioName
     * @param serviceName
     * @return
     */
    public double GetDiskDemand(String scenarioName, String serviceName) {
        String sXPathExpression = String.format(MODEL_SCENARIO_DEMAND_DISK, scenarioName, serviceName);
        return this.GetXPathModelDouble(sXPathExpression);
    }

    /**
     * Get the total population specified in the model. This value is not
     * related in any way with the workload mixes.
     *
     * @return the total population specified in the model.
     */
    public double GetPopulation() {
        return this.GetXPathModelDouble(MODEL_POPULATION);
    }

    /**
     * Get the population for a single scenario for the first workload mix.
     *
     * @param scenarioName the scenario for which to get the population.
     * @return the population for the specified scenario.
     */
    public double GetPopulation(String scenarioName) {
        return this.GetPopulation(0, scenarioName);
    }

    /**
     * Get the population for a scenario in a workload mix.
     *
     * @param mixNo        the index of the workload mix.
     * @param scenarioName the scenario for which to get the population.
     * @return the population for the specified scenario, in the specified
     * workload mix.
     */
    public double GetPopulation(int mixNo, String scenarioName) {
        String sXPathExpression = String.format(MODEL_POPULATION_SCENARIO, mixNo + 1, scenarioName);
        return this.GetXPathModelDouble(sXPathExpression);
    }

    /**
     * Get the number of CPUs for a node.
     *
     * @param nodeName the node for which to get the number of CPUs.
     * @return the number of CPUs for the specified node.
     */
    public int GetCpuMultiplicity(String nodeName) {
        String sXPathExpression = String.format(MODEL_NODE_CPU_MULTIPLICITY, nodeName);
        return this.GetXPathModelInt(sXPathExpression);
    }

    /**
     * Get the number of DISKs for a node.
     *
     * @param nodeName the node for which to get the number of DISKs.
     * @return the number of DISKs for the specified node.
     */
    public int GetDiskMultiplicity(String nodeName) {
        String sXPathExpression = String.format(MODEL_NODE_DISK_MULTIPLICITY, nodeName);
        return this.GetXPathModelInt(sXPathExpression);
    }

    /**
     * Set to 0 the number of users for each scenario in the mix.
     */
    public void ResetPerClassPopulation() {
        String sXPathExpression = "OperaModel/Workloads/WorkloadMixes/Mix/@load";
        try {
            NodeList attrLoads = (NodeList) this.m_xPath.evaluate(sXPathExpression, this.doc, XPathConstants.NODESET);
            for (int i = 0; i < attrLoads.getLength(); ++i) {
                attrLoads.item(i).setNodeValue("0");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Get the response time for scenario <code>scenarioName</code> for the
     * first workload mix, as specified in the model.
     *
     * @param scenarioName - The scenario for which to get the response time;
     * @return A number which represent the response time as computed by the
     * model.
     */
    public double GetResponseTimeScenario(String scenarioName) {
        return this.GetResponseTimeScenario(0, scenarioName);
    }

    /**
     * Get the response time for scenario <code>scenarioName</code> for the
     * workload mix <code>mixNo</code>, as specified in the model.
     *
     * @param mixNo        - The index of the workload mix (starts at 0).
     * @param scenarioName - The name of the scenario.
     * @return A number which represent the response time as computed by the
     * model.
     */
    public double GetResponseTimeScenario(int mixNo, String scenarioName) {
        String sXPathExpression = String.format(RESULTS_RESPONSE_TIME_SCENARIO, mixNo + 1, scenarioName);
        return this.GetXPathResultsDouble(sXPathExpression);
    }

    /**
     * Get the response time for multiple scenarios, for the first workload mix,
     * as specified in the model.
     *
     * @param scenarioNames - An array with scenario names for which to get the response
     *                      time.
     * @return An array with response times. The element at index
     * <code>idx</code> in this array corresponds to the scenario
     * <code>idx</code> from the array <code>scenarioNames</code>.
     */
    public double[] GetResponseTimeScenario(String[] scenarioNames) {
        return this.GetResponseTimeScenario(0, scenarioNames);
    }

    /**
     * Get the response time for multiple scenarios.
     *
     * @param mixNo         the workload mix for which to get the response time.
     * @param scenarioNames an array with scenario names for which to get the response
     *                      time.
     * @return An array with response times. The element at index
     * <code>idx</code> in this array corresponds to the scenario
     * <code>idx</code> from the array <code>scenarioNames</code>.
     */
    public double[] GetResponseTimeScenario(int mixNo, String[] scenarioNames) {
        double[] responseTimes = new double[scenarioNames.length];
        for (int i = 0; i < scenarioNames.length; ++i) {
            responseTimes[i] = this.GetResponseTimeScenario(mixNo, scenarioNames[i]);
        }
        return responseTimes;
    }

    public double GetResponseTimeContainer(String containerName, String scenarioName) {
        return this.GetResponseTimeContainer(0, containerName, scenarioName);
    }

    public double GetResponseTimeContainer(int mixNo, String containerName, String scenarioName) {
        String sXPathExpression = String.format(RESULTS_RESPONSE_TIME_CONTAINER, mixNo + 1, containerName,
                scenarioName);
        return this.GetXPathResultsDouble(sXPathExpression);
    }

    public double GetResponseTimeService(String serviceName, String scenarioName) {
        return this.GetResponseTimeService(0, serviceName, scenarioName);
    }

    public double GetResponseTimeService(int mixNo, String serviceName, String scenarioName) {
        String sXPathExpression = String.format(RESULTS_RESPONSE_TIME_SERVICE, mixNo + 1, serviceName, scenarioName);
        return this.GetXPathResultsDouble(sXPathExpression);
    }

    public double GetUtilizationContainer(String containerName) {
        return this.GetUtilizationContainer(0, containerName);
    }

    public double GetUtilizationContainer(int mixNo, String containerName) {
        // String sXPathExpression =
        // "/Results/Architecture/Workloads/Container[@name=\"" + containerName
        // + "\"]/@Utilization";
        String sXPathExpression = String.format(RESULTS_UTILIZATION_CONTAINER, mixNo + 1, containerName);
        return this.GetXPathResultsDouble(sXPathExpression);
    }

    public double GetUtilizationService(String serviceName) {
        return this.GetUtilizationService(0, serviceName);
    }

    public double GetUtilizationService(int mixNo, String serviceName) {
        // String sXPathExpression =
        // "/Results/Architecture/Workloads/Service[@name=\"" + serviceName +
        // "\"]/@Utilization";
        String sXPathExpression = String.format(RESULTS_UTILIZATION_SERVICE, mixNo + 1, serviceName);
        return this.GetXPathResultsDouble(sXPathExpression);
    }

    public double GetUtilizationNode(String nodeName, String deviceName) {
        return this.GetUtilizationNode(0, nodeName, deviceName);
    }

    public double GetUtilizationNode(int mixNo, String nodeName, String deviceName) {
        String sXPathExpression = String.format(RESULTS_UTILIZATION_NODE, mixNo + 1, nodeName, deviceName);
        return this.GetXPathResultsDouble(sXPathExpression);
    }

    public double GetThroughput(String scenarioName) {
        return this.GetThroughput(0, scenarioName);
    }

    public double[] GetThroughput(String[] scenarioNames) {
        double[] throughput = new double[scenarioNames.length];
        for (int i = 0; i < scenarioNames.length; ++i) {
            throughput[i] = this.GetThroughput(scenarioNames[i]);
        }
        return throughput;
    }

    public double GetThroughput(int mixNo, String scenarioName) {
        // "mixNo" starts from 0, but indices in XPath start with 1.
        // String sXPathExpression = "/Results/Architecture/Workloads[" + (mixNo
        // + 1) + "]/Scenario[@name=\"" + scenarioName +
        // "\"]/Throughput/text()";
        String sXPathExpression = String.format(RESULTS_THROUGHPUT, mixNo + 1, scenarioName);
        return this.GetXPathResultsDouble(sXPathExpression);
    }

    public double GetThinkTime(String strScenario) {
        String sXPathExpression = String.format(MODEL_THINK_TIME_SCENARIO, strScenario);
        return this.GetXPathModelDouble(sXPathExpression);
    }

    /**
     * Get the value identified by the XPath expression as a <code>double</code>
     * . The value is searched in the model document.
     *
     * @param sXPathExpression <p>
     *                         an XPath expression identifying the desired value in the PXL
     *                         document.
     *                         </p>
     *                         <p>
     *                         This expression must start with <code>/Model/</code>.
     *                         </p>
     * @return <ul>
     * <li>if <code>sXPathExpression</code> refers to a numeric value,
     * will return that value as a <code>double</code>.</li>
     * <p>
     * <li><code>Double.NaN</code> otherwise.</li>
     * </ul>
     */
    public double GetXPathModelDouble(String sXPathExpression) {
        Number value = this.GetXPathValueNumber(this.doc, sXPathExpression);
        return value.doubleValue();
    }

    /**
     * <p>
     * Get the value identified by the XPath expression as a <code>double</code>
     * . The value is searched in the results document.
     * <p>
     * <p>
     * <p>
     * This method should be called only after calling the <code>solve()</code>
     * method and will return the value produced by the last call to
     * <code>solve()</code>.
     * <p>
     *
     * @param sXPathExpression <p>
     *                         an XPath expression identifying the desired value in the
     *                         results document.
     *                         </p>
     *                         <p>
     *                         This expression must start with <code>/Results/</code>.
     *                         </p>
     * @return <ul>
     * <li>if <code>sXPathExpression</code> refers to a numeric value,
     * will return that value as a <code>double</code>.</li>
     * <p>
     * <li><code>Double.NaN</code> otherwise.</li>
     * </ul>
     */
    public double GetXPathResultsDouble(String sXPathExpression) {
        Number value = this.GetXPathValueNumber(this.results, sXPathExpression);
        return value.doubleValue();
    }

    /**
     * Get the value identified by the XPath expression as a <code>double</code>
     * . The XPath expression can refer to the model document (and starts with
     * <code>/Model/</code>) or the results document (and starts with
     * <code>/Results/</code>). If the XPath starts with anything else, this
     * method return <code>Double.NaN</code>.
     *
     * @param sXPathExpression <p>
     *                         an XPath expression identifying the desired value in the PXL
     *                         or results document.
     *                         </p>
     * @return <ul>
     * <li>if <code>sXPathExpression</code> refers to a numeric value,
     * will return that value as a <code>double</code>.</li>
     * <p>
     * <li><code>Double.NaN</code> otherwise.</li>
     * </ul>
     */
    public double GetXPathDouble(String sXPathExpression) {
        if (sXPathExpression.startsWith("/Model/")) {
            return this.GetXPathModelDouble(sXPathExpression);
        } else if (sXPathExpression.startsWith("/Results/")) {
            return this.GetXPathResultsDouble(sXPathExpression);
        }

        return Double.NaN;
    }

    public int GetXPathModelInt(String sXPathExpression) {
        Number value = this.GetXPathValueNumber(this.doc, sXPathExpression);
        return value.intValue();
    }

    public int GetXPathResultsInt(String sXPathExpression) {
        Number value = this.GetXPathValueNumber(this.results, sXPathExpression);
        return value.intValue();
    }

    private Number GetXPathValueNumber(Node node, String sXPathExpression) {
        try {
            // We get the value as a string, because if the number is in
            // scientific notation
            // getting it as a NUMBER will fail. Also this method is slightly
            // faster than getting
            // the value as a number.
            String sVal = (String) this.m_xPath.evaluate(sXPathExpression, node, XPathConstants.STRING);
            return (Number) Double.parseDouble(sVal);
        } catch (Exception e) {
        }
        return Double.NaN;
    }

    private String GetXPathValueString(Node node, String sXPathExpression) {
        try {
            return (String) this.m_xPath.evaluate(sXPathExpression, node, XPathConstants.STRING);
        } catch (Exception e) {
        }
        return null;
    }

    /**
     * Select a set of nodes that match the specified XPath expression, and get
     * their values as strings.
     *
     * @param sXPathExpression The expression used to select the nodes.
     * @return The values of the nodes that matched the specified XPath
     * expression. If an exception occurred, the function returns
     * <code>null<code>.
     */
    private String[] GetXPathArrayString(Node node, String sXPathExpression) {
        String[] values = null;
        try {
            NodeList lstNodes = (NodeList) this.m_xPath.evaluate(sXPathExpression, node, XPathConstants.NODESET);
            values = new String[lstNodes.getLength()];

            for (int i = 0; i < lstNodes.getLength(); ++i) {
                values[i] = lstNodes.item(i).getNodeValue();
            }
        } catch (Exception e) {
        }

        return values;
    }

    public void solve() {
        model.readAndValidatePxl(doc);
        model.initialize();

        // open the output file
        model.setOutDoc(new org.apache.xerces.dom.DocumentImpl());
        model.findConfigurations();
        results = model.getResultsDocument();
    }

    public ArrayList<double[]> getWorstCaseWorkloadMixex() {
        return model.getWorstWorkloadMixes();
    }

    private void SaveDocument(java.io.Writer writer, Document doc) {
        // Set up the output transformer
        try {
            Transformer xmlTransformer = TransformerFactory.newInstance().newTransformer();
            xmlTransformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            xmlTransformer.setOutputProperty(OutputKeys.INDENT, "yes");
            xmlTransformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            xmlTransformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

            javax.xml.transform.stream.StreamResult result = new javax.xml.transform.stream.StreamResult(writer);
            javax.xml.transform.dom.DOMSource source = new javax.xml.transform.dom.DOMSource(doc);
            xmlTransformer.transform(source, result);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void SaveModel(java.io.Writer writer) {
        this.SaveDocument(writer, doc);
    }

    public void SaveResults(java.io.Writer writer) {
        this.SaveDocument(writer, results);
    }

    public void SaveModelToXmlFile(String sFileName) {
        try {
            FileWriter outputFile = new FileWriter(sFileName);
            this.SaveDocument(outputFile, doc);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void SaveResultsToXmlFile(String sFileName) {
        try {
            FileWriter outputFile = new FileWriter(sFileName);
            this.SaveDocument(outputFile, results);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void writeToFile(ModelParameter[] modelParameter) {
        StringBuilder line = new StringBuilder();
        for (ModelParameter mp : modelParameter) {
            line.append(String.valueOf(formatter.format(mp.getValue())) + ",");
        }
        writer.println(line);
    }

    public static void main(String[] args) throws FileNotFoundException, UnsupportedEncodingException {

        OperaModel theModel = new OperaModel();

        theModel.setModel("./input/BigDataApp.model.pxl");

        KalmanEstimator theEstimator = null;
        KalmanConfiguration kalmanConfig = new KalmanConfiguration();
        kalmanConfig.withConfigFile("./input/BigDataApp.kalman.config")
                .withModel(theModel)
                .withSetting(KalmanConfiguration.ITERATIONS_MAX, "10");

        theEstimator = new KalmanEstimator(kalmanConfig);
        MeasuresUtil rm = new MeasuresUtil("./input/metrics2.txt", 80, 5);

        ArrayList arrivals = rm.getArrivals();

        HashMap metrics = rm.getMetrics();

        ArrayList cpuLBUtil = (ArrayList) metrics.get("cpuLBUtil");
        ArrayList cpuWebUtil = (ArrayList) metrics.get("cpuWebUtil");
        ArrayList cpuAnalyticUtil = (ArrayList) metrics.get("cpuAnalyticUtil");
        ArrayList cpuDBUtil = (ArrayList) metrics.get("cpuDBUtil");
        ArrayList respTime = (ArrayList) metrics.get("respTime");
        ArrayList throughput = (ArrayList) metrics.get("throughput");

        int noOfSenarios = 1;
        int thinkTime = 500;

        // remove this loop if you don't want calibration
        for (int i = 0; i < rm.getNoOfMeasurs(); i++) // there are 30 samples
        {
            // put the workload here (arrival rate: req/s); should contain workload foreach scenario
            Double workload = (Double) arrivals.get(i);
            // put the values here, keep the order from the kalman config files
            // the values are: CPU utilization web, CPU Analytic, CPU utilization db, response times for
            // each scenario, throughput for each scenario
            Double responseTime = (Double) respTime.get(i);

            // set workload in the model
            for (int j = 0; j < noOfSenarios; j++) {
                theModel.SetPopulation("select 0", workload * (thinkTime + responseTime));
                // the response time should be in the "measuredMetrics" vector
            }
            theModel.solve();

//            calibrate model;
            double[] measuredMetrics = {(Double) cpuLBUtil.get(i), (Double) cpuWebUtil.get(i),
                    (Double) cpuAnalyticUtil.get(i), (Double) cpuDBUtil.get(i), responseTime, (Double) throughput.get(i)};
            EstimationResults results = theEstimator.EstimateModelParameters(measuredMetrics);
            System.out.println(results.toString());
            ModelParameter[] mp = results.getModelParametersFinal();
            theModel.writeToFile(mp);
        }
        theModel.SaveModelToXmlFile("./output/FinalModel.pxl");
        theModel.writer.close();
    }
//    public static void main(String[] args) throws FileNotFoundException, UnsupportedEncodingException {
//
//        OperaModel theModel = new OperaModel();
//        theModel.setModel("./input/BigDataApp.model.pxl");
//        int records = 600;
//        MeasuresUtil rm = new MeasuresUtil("./input/metrics2.txt", 200, records);
//        HashMap<String, Double> demands = rm.getAvgCPUDemands();
//        HashMap<String, ArrayList<Double>> metrics = rm.getMetrics();
//
//        for(int i = 0; i<records; i++){
//            theModel.SetCpuDemand("select 0", "LBServer", metrics.get("cpuLBUtil").get(i)/metrics.get("throughput").get(i));
//            theModel.SetCpuDemand("select 0", "WebServer", metrics.get("cpuWebUtil").get(i)/metrics.get("throughput").get(i));
//            theModel.SetCpuDemand("select 0", "AnalyticServer", metrics.get("cpuAnalyticUtil").get(i)/metrics.get("throughput").get(i));
//            theModel.SetCpuDemand("select 0", "Database", metrics.get("cpuDBUtil").get(i)/metrics.get("throughput").get(i));
//            theModel.solve();
//
//        }
//
//        System.out.println("Throughput: " + theModel.GetThroughput("select 0") + " -- " + rm.getAveMetrics().get("throughput"));
//        System.out.println("Response Time: " + theModel.GetResponseTimeScenario("select 0") + " -- " + rm.getAveMetrics().get("respTime"));
//        System.out.println("CPU Utilization Web: " + theModel.GetUtilizationContainer("WebContainer") + " -- " + rm.getAveMetrics().get("aveCPUWebUtil"));
//        System.out.println("CPU Utilization Analytic: " + theModel.GetUtilizationContainer("AnalyticContainer") + " -- " + rm.getAveMetrics().get("aveCPUAnalyticUtil"));
//
//
//        theModel.SaveModelToXmlFile("./output/FinalModel.pxl");
//    }

}
