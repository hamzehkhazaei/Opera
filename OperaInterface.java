package opera;

import opera.KalmanFilter.EstimationResults;
import opera.KalmanFilter.KalmanConfiguration;
import opera.KalmanFilter.KalmanEstimator;
import opera.KalmanFilter.ModelParameter;
import util.MeasuresUtil;
import util.MetricCollection;

import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by hamzeh on 2016-08-18.
 * This model is used for ICSE17.
 */
public class OperaInterface {

    private OperaModel operaModel = null;
    private String inputModel;
    private String kalmanConfigFile;
    private String metricsFile;
    private String kalmanIterationCount;
    private int startLine;
    private int sampleNo;
    private int noOfScenarios;
    private String finalModel;

    public OperaInterface(String inpModel, String kalmanConfFile, String metricFile, String kalmanIterationCnt,
                          int startPoint, int smpleNo, int noOfScen, String fModel) {
        inputModel = inpModel;
        kalmanConfigFile = kalmanConfFile;
        metricsFile = metricFile;
        kalmanIterationCount = kalmanIterationCnt;
        startLine = startPoint;
        sampleNo = smpleNo;
        noOfScenarios = noOfScen;
        finalModel = fModel;

        try {
            operaModel = new OperaModel();
            operaModel.setModel(inputModel);

            KalmanConfiguration kalmanConfig = new KalmanConfiguration();
            kalmanConfig.withConfigFile(kalmanConfigFile)
                    .withModel(operaModel)
                    .withSetting(KalmanConfiguration.ITERATIONS_MAX, kalmanIterationCount);

            operaModel.theEstimator = new KalmanEstimator(kalmanConfig);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * This method can be used to train the model based on previous measurements.
     *
     * @throws FileNotFoundException
     * @throws UnsupportedEncodingException
     */
    private void trainModel() throws FileNotFoundException, UnsupportedEncodingException {

        EstimationResults results = null;
        ModelParameter[] mp = null;

        MeasuresUtil rm = new MeasuresUtil(metricsFile, startLine, sampleNo, MeasuresUtil.FILE_TYPE_2);

        HashMap metrics = rm.getMetrics();
        ArrayList cpuLBUtil = (ArrayList) metrics.get("cpuLBUtil");
        ArrayList cpuWebUtil = (ArrayList) metrics.get("cpuWebUtil");
        ArrayList cpuAnalyticUtil = (ArrayList) metrics.get("cpuAnalyticUtil");
        ArrayList cpuDBUtil = (ArrayList) metrics.get("cpuDBUtil");
        ArrayList respTime = (ArrayList) metrics.get("respTime");
        ArrayList throughput = (ArrayList) metrics.get("throughput");
        ArrayList workload = (ArrayList) metrics.get("workload");
        ArrayList arrival = (ArrayList) metrics.get("arrival");
        ArrayList contNoWeb = (ArrayList) metrics.get("contNoWeb");
        ArrayList contNoAnalytic = (ArrayList) metrics.get("contNoAnalytic");


        // remove this loop if you don't want calibration
        for (int i = 0; i < rm.getNoOfMeasurs(); i++) // there are 30 samples
        {
            // put the workload here (arrival rate: req/s); should contain workload foreach scenario
            Double wl = (Double) workload.get(i);
            Double arr = (Double) arrival.get(i);

            // put the values here, keep the order from the kalman config files
            // the values are: CPU utilization web, CPU Analytic, CPU utilization db, response times for
            // each scenario, throughput for each scenario
            Double responseTime = (Double) respTime.get(i);

            int noOfWebContainers = ((Double) contNoWeb.get(i)).intValue();
            int noOfAnalyticContainers = ((Double) contNoAnalytic.get(i)).intValue();

            double[] measuredMetrics = {(Double) cpuLBUtil.get(i), (Double) cpuWebUtil.get(i),
                    (Double) cpuAnalyticUtil.get(i), (Double) cpuDBUtil.get(i), responseTime, (Double) throughput.get(i)};

            // set workload and no of containers in the model
            for (int j = 0; j < noOfScenarios; j++) {
                // based on formula: wl = arrivals *(thinkTime + responseTime)
                operaModel.SetPopulation("select 0", wl);
                operaModel.SetNodeMultiplicity("WebHost", noOfWebContainers);
                operaModel.SetNodeMultiplicity("AnalyticHost1", noOfAnalyticContainers);
                // the response time should be in the "measuredMetrics" vector
            }
            operaModel.solve();

            // for the training we always do the calibration to get the model fully trained.
            results = operaModel.theEstimator.EstimateModelParameters(measuredMetrics);
            mp = results.getModelParametersFinal();

            // I store last values to see if they changed

            // comment following line to not to have the tabular output.
            System.out.println(results.toString());
            double[] measMetricsToSave = {wl, arr,
                    (Double) cpuWebUtil.get(i), (Double) cpuAnalyticUtil.get(i), (Double) cpuDBUtil.get(i),
                    responseTime, (Double) throughput.get(i), noOfWebContainers, noOfAnalyticContainers, 1};

            operaModel.writeMeasAndEstToFile(measMetricsToSave);
            operaModel.writeDemandsToFile(mp);
        }

        operaModel.SaveModelToXmlFile(finalModel);

        operaModel.writerForDemand.close();
        operaModel.writerForMeasAndEst.close();
    }

    /**
     * This method returs a sample metrics
     * @return a sample metrics that we can get at run-time
     */
    public MetricCollection createSampleMetrics() {
        MetricCollection smplMetrics = new MetricCollection();
        smplMetrics.Add("docker.cpu-utilization", "legis/legis.load-balancer", 0.2);
        smplMetrics.Add("docker.cpu-utilization", "legis/legis.web-worker.*", 0.31);
        smplMetrics.Add("docker.cpu-utilization", "legis/legis.spark-worker.*", 0.45);
        smplMetrics.Add("docker.cpu-utilization", "legis/cassandra.*", 0.55);
        smplMetrics.Add("throughput", "legis/legis.load-balancer/find-routes", 0.0001);
        smplMetrics.Add("response-time", "legis/legis.load-balancer/find-routes", 251);
        smplMetrics.Add("count-users", "legis/legis.load-balancer/find-routes", 1.2);
        smplMetrics.Add("spark-containers-cnt", "legis", 2);
        smplMetrics.Add("web-containers-cnt", "legis", 1);

        smplMetrics.Add("cpu-demand", "opera/legis/proxy-virtual", operaModel.GetCpuDemand("select 0", "ProxyServer"));
        smplMetrics.Add("cpu-demand", "opera/legis/cassandra", operaModel.GetCpuDemand("select 0", "Database"));
        smplMetrics.Add("cpu-demand", "opera/legis/load-balancer", operaModel.GetCpuDemand("select 0", "LBServer"));
        smplMetrics.Add("cpu-demand", "opera/legis/web-workers", operaModel.GetCpuDemand("select 0", "WebServer"));
        smplMetrics.Add("cpu-demand", "opera/legis/spark-workers", operaModel.GetCpuDemand("select 0", "AnalyticServer1"));
        smplMetrics.Add("cpu-demand", "opera/legis/spark-virtual", operaModel.GetCpuDemand("select 0", "AnalyticServer2"));

        return smplMetrics;
    }

    /**
     * This method returns the new number of web and analytic containers to bring the cpu utilization to the
     * desired range.
     *
     * @param smplMetrics
     * @param webCPULowUtil
     * @param webCPUUPUtil
     * @param analyticCPULowUtil
     * @param analyticCPUUPUtil
     * @return the new topology that brings the system back to the desired area.
     */
    public ArrayList<Integer> getContainersCnt(MetricCollection smplMetrics, Double webCPULowUtil, Double webCPUUPUtil,
                                               Double analyticCPULowUtil, Double analyticCPUUPUtil) {
        // add data from the model
        // Cornel did the following Adds; I think we don't need this them. We already have the smpleMetrics filled.
        smplMetrics.Add("cpu-utilization", "opera/legis/proxy-virtual", 100 * operaModel.GetUtilizationNode("ProxyHost", "CPU"));
        smplMetrics.Add("cpu-utilization", "opera/legis/cassandra", 100 * operaModel.GetUtilizationNode("DataHost", "CPU"));
        smplMetrics.Add("cpu-utilization", "opera/legis/load-balancer", 100 * operaModel.GetUtilizationNode("LBHost", "CPU"));
        smplMetrics.Add("cpu-utilization", "opera/legis/web-workers", 100 * operaModel.GetUtilizationNode("WebHost", "CPU"));
        smplMetrics.Add("cpu-utilization", "opera/legis/spark-workers", 100 * operaModel.GetUtilizationNode("AnalyticHost1", "CPU"));
        smplMetrics.Add("cpu-utilization", "opera/legis/spark-virtual", 100 * operaModel.GetUtilizationNode("AnalyticHost2", "CPU"));
        smplMetrics.Add("response-time", "opera/legis/find-routes", operaModel.GetResponseTimeScenario("select 0"));
        smplMetrics.Add("throughput", "opera/legis/find-routes", 1000 * operaModel.GetThroughput("select 0"));

        smplMetrics.Add("cpu-demand", "opera/legis/proxy-virtual", operaModel.GetCpuDemand("select 0", "ProxyServer"));
        smplMetrics.Add("cpu-demand", "opera/legis/cassandra", operaModel.GetCpuDemand("select 0", "Database"));
        smplMetrics.Add("cpu-demand", "opera/legis/load-balancer", operaModel.GetCpuDemand("select 0", "LBServer"));
        smplMetrics.Add("cpu-demand", "opera/legis/web-workers", operaModel.GetCpuDemand("select 0", "WebServer"));
        smplMetrics.Add("cpu-demand", "opera/legis/spark-workers", operaModel.GetCpuDemand("select 0", "AnalyticServer1"));
        smplMetrics.Add("cpu-demand", "opera/legis/spark-virtual", operaModel.GetCpuDemand("select 0", "AnalyticServer2"));

        ArrayList<Integer> newDeployment = new ArrayList<Integer>();
        Double sparkContainersCnt = smplMetrics.Get("spark-containers-cnt", "legis");
        Double webContainerCnt = smplMetrics.Get("web-containers-cnt", "legis");
        Double webCPUUtil = smplMetrics.GetAverage("docker.cpu-utilization", "legis/legis.web-worker.*") / 100;
        Double analyticCPUUtil = smplMetrics.GetAverage("docker.cpu-utilization", "legis/legis.spark-worker.*") / 100;
        webCPULowUtil /= 100;
        webCPUUPUtil /= 100;
        analyticCPULowUtil /= 100;
        analyticCPUUPUtil /= 100;

        int webContNo = webContainerCnt.intValue();
        int analyticContNo = sparkContainersCnt.intValue();
        // to control the autonomic manager in case the Opera model did not converge.
        int controlCounter = 1;

        // if Web cluster is under utilized scale it down one by one until bring it to the normal range or we get down to
        // one web container.
        if (webCPUUtil < webCPULowUtil && webContNo > 1) { // should check if we have more than one container as well
            do {
                operaModel.SetNodeMultiplicity("WebHost", --webContNo);
                operaModel.solve();
                controlCounter++;
            }
            while (operaModel.GetUtilizationNode("WebHost", "CPU") < webCPULowUtil && controlCounter < 4 && webContNo > 1);
        }

        // if Spark cluster is under utilized scale it down one by one until bring it to the normal range.
        // or we get down to one analytic container.
        controlCounter = 1;
        if (analyticCPUUtil < analyticCPULowUtil && analyticContNo > 1) {
            do {
                operaModel.SetNodeMultiplicity("AnalyticHost1", --analyticContNo);
                operaModel.solve();
                controlCounter++;
            }
            while (operaModel.GetUtilizationNode("AnalyticHost1", "CPU") < analyticCPULowUtil && controlCounter < 4 && analyticContNo > 1);
        }

        // if tomcat web server is over utilized scale it up one by one until bring it to the normal range.
        if (webCPUUtil > webCPUUPUtil) {
            do {
                operaModel.SetNodeMultiplicity("WebHost", ++webContNo);
                operaModel.solve();
                controlCounter++;
            }
            while (operaModel.GetUtilizationNode("WebHost", "CPU") > webCPUUPUtil && controlCounter < 4);
        }

        // if Spark cluster is over utilized scale it up one by one until bring it to the normal range.
        controlCounter = 1;
        if (analyticCPUUtil > analyticCPUUPUtil) {
            do {
                operaModel.SetNodeMultiplicity("AnalyticHost1", ++analyticContNo);
                operaModel.solve();
                controlCounter++;
            }
            while (operaModel.GetUtilizationNode("AnalyticHost1", "CPU") > analyticCPUUPUtil && controlCounter < 4);
        }

        newDeployment.add(webContNo);
        newDeployment.add(analyticContNo);

        System.out.println(newDeployment);

        return newDeployment;
    }

    /**
     * This method calibrate the model with the latest measured metrics from run-time system
     *
     * @param smplMetrics is the new metrics that we use to calibrate model
     */
    public void calibrateModel(MetricCollection smplMetrics) {
        Double cpuLBUtil = smplMetrics.Get("docker.cpu-utilization", "legis/legis.load-balancer") / 100;
        Double cpuWebUtil = smplMetrics.GetAverage("docker.cpu-utilization", "legis/legis.web-worker.*") / 100;
        Double cpuAnalyticUtil = smplMetrics.GetAverage("docker.cpu-utilization", "legis/legis.spark-worker.*") / 100;
        Double cpuDBUtil = smplMetrics.GetAverage("docker.cpu-utilization", "legis/cassandra.*") / 100;
        Double throughput = smplMetrics.Get("throughput", "legis/legis.load-balancer/find-routes") / 1000;
        Double respTime = smplMetrics.Get("response-time", "legis/legis.load-balancer/find-routes");
        Double cntUsers = smplMetrics.Get("count-users", "legis/legis.load-balancer/find-routes");
        Double sparkContainersCnt = smplMetrics.Get("spark-containers-cnt", "legis");
        Double webContainerCnt = smplMetrics.Get("web-containers-cnt", "legis");

        operaModel.SetNodeMultiplicity("WebHost", webContainerCnt.intValue());
        operaModel.SetNodeMultiplicity("AnalyticHost1", sparkContainersCnt.intValue());
        operaModel.SetPopulation("select 0", cntUsers);
        operaModel.solve();

        double[] measuredMetrics = {cpuLBUtil, cpuWebUtil, cpuAnalyticUtil, cpuDBUtil, respTime, throughput};
        EstimationResults res = operaModel.theEstimator.EstimateModelParameters(measuredMetrics);
        System.out.println(res);
    }


    public static void main(String[] args) throws FileNotFoundException, UnsupportedEncodingException {
        OperaInterface oi = new OperaInterface("./input/BigDataApp.model.pxl", "./input/BigDataApp.kalman.config",
                "./input/exp4_metrics.txt", "15", 80, 1, 1, "./output/FinalBigDataAppModel.pxl");
        oi.trainModel();
        MetricCollection smplMetrics = oi.createSampleMetrics();
        oi.calibrateModel(smplMetrics);
        oi.getContainersCnt(smplMetrics, 20.0, 80.0, 20.0, 80.0);
    }
}
