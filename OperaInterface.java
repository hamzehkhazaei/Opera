package opera;

import opera.KalmanFilter.EstimationResults;
import opera.KalmanFilter.KalmanConfiguration;
import opera.KalmanFilter.KalmanEstimator;
import util.MetricCollection;

import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;

/**
 * Created by hamzeh on 2016-08-18.
 * This model is used for ICSE17.
 */
public class OperaInterface {

    public static final int NO_SCENARIOS = 1;
    public static final int THINK_TIME = 500;

    private OperaModel operaModel = null;
    private KalmanEstimator theEstimator;
    private int thinkTime;
    private String inputModel;
    private String kalmanConfigFile;
    private String metricsFile;
    private String kalmanIterationCount;
    private int startLine;
    private int sampleNo;
    private int noOfScenarios;
    private String finalModel;


    public OperaInterface(String inputModel, String kalmanConfigFile, String metricsFile, String kalmanIterationCount,
                          int thinkTime, int startLine, int sampleNo, int noOfScenarios, String finalModel) {
        this.thinkTime = thinkTime;
        this.inputModel = inputModel;
        this.kalmanConfigFile = kalmanConfigFile;
        this.metricsFile = metricsFile;
        this.kalmanIterationCount = kalmanIterationCount;
        this.startLine = startLine;
        this.sampleNo = sampleNo;
        this.noOfScenarios = noOfScenarios;
        this.finalModel = finalModel;

        try {
            operaModel = new OperaModel();
            operaModel.setModel(inputModel);

            KalmanConfiguration kalmanConfig = new KalmanConfiguration();
            kalmanConfig.withConfigFile(kalmanConfigFile)
                    .withModel(operaModel)
                    .withSetting(KalmanConfiguration.ITERATIONS_MAX, kalmanIterationCount);

            theEstimator = new KalmanEstimator(kalmanConfig);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

	    /*
        private void trainModel() throws FileNotFoundException, UnsupportedEncodingException {
	        operaModel.setModel(inputModel);

	        KalmanConfiguration kalmanConfig = new KalmanConfiguration();
	        kalmanConfig.withConfigFile(kalmanConfigFile)
	                .withModel(operaModel)
	                .withSetting(KalmanConfiguration.ITERATIONS_MAX, kalmanIterationCount);

	        theEstimator = new KalmanEstimator(kalmanConfig);
	        MeasuresUtil rm = new MeasuresUtil(metricsFile, startLine, sampleNo, MeasuresUtil.FILE_TYPE_2);

	        ArrayList<Double> arrivals = rm.getArrivals();

	        HashMap metrics = rm.getMetrics();

	        ArrayList cpuLBUtil = (ArrayList) metrics.get("cpuLBUtil");
	        ArrayList cpuWebUtil = (ArrayList) metrics.get("cpuWebUtil");
	        ArrayList cpuAnalyticUtil = (ArrayList) metrics.get("cpuAnalyticUtil");
	        ArrayList cpuDBUtil = (ArrayList) metrics.get("cpuDBUtil");
	        ArrayList<Double> respTime = (ArrayList) metrics.get("respTime");
	        ArrayList throughput = (ArrayList) metrics.get("throughput");

	        // remove this loop if you don't want calibration
	        for (int i = 0; i < rm.getNoOfMeasurs(); i++) // there are 30 samples
	        {
	            // put the workload here (arrival rate: req/s); should contain workload foreach scenario
	            Double workload = arrivals.get(i);
	            // put the values here, keep the order from the kalman config files
	            // the values are: CPU utilization web, CPU Analytic, CPU utilization db, response times for
	            // each scenario, throughput for each scenario
	            Double responseTime = respTime.get(i);

	            // set workload in the model
	            for (int j = 0; j < noOfScenarios; j++) {
	                operaModel.SetPopulation("select 0", workload * (thinkTime + responseTime));
	                // the response time should be in the "measuredMetrics" vector
	            }
	            operaModel.solve();

	            double[] measuredMetrics = {(Double) cpuLBUtil.get(i), (Double) cpuWebUtil.get(i),
	                    (Double) cpuAnalyticUtil.get(i), (Double) cpuDBUtil.get(i), responseTime, (Double) throughput.get(i)};
	            EstimationResults results = theEstimator.EstimateModelParameters(measuredMetrics);
	            System.out.println(results.toString());
	            ModelParameter[] mp = results.getModelParametersFinal();
	            operaModel.writeToFile(mp);
	        }
	        operaModel.SaveModelToXmlFile(finalModel);
	    }
	    */

    /*
     * this method returns the new number of web and analytic containers to bring the cpu utilization to the
     * desired range.
     */
    public ArrayList<Integer> getContainersCnt(MetricCollection theMetrics, Double webCPULowUtil, Double webCPUUPUtil,
                                               Double analyticCPULowUtil, Double analyticCPUUPUtil) {
        //calibrateModel(theMetrics);
        // add data from the model
        theMetrics.Add("cpu-utilization", "opera/legis/proxy-virtual", 100 * operaModel.GetUtilizationNode("ProxyHost", "CPU"));
        theMetrics.Add("cpu-utilization", "opera/legis/cassandra", 100 * operaModel.GetUtilizationNode("DataHost", "CPU"));
        theMetrics.Add("cpu-utilization", "opera/legis/load-balancer", 100 * operaModel.GetUtilizationNode("LBHost", "CPU"));
        theMetrics.Add("cpu-utilization", "opera/legis/web-workers", 100 * operaModel.GetUtilizationNode("WebHost", "CPU"));
        theMetrics.Add("cpu-utilization", "opera/legis/spark-workers", 100 * operaModel.GetUtilizationNode("AnalyticHost1", "CPU"));
        theMetrics.Add("cpu-utilization", "opera/legis/spark-virtual", 100 * operaModel.GetUtilizationNode("AnalyticHost2", "CPU"));
        theMetrics.Add("response-time", "opera/legis/find-routes", operaModel.GetResponseTimeScenario("select 0"));
        theMetrics.Add("throughput", "opera/legis/find-routes", 1000 * operaModel.GetThroughput("select 0"));

        theMetrics.Add("cpu-demand", "opera/legis/proxy-virtual", operaModel.GetCpuDemand("select 0", "ProxyServer"));
        theMetrics.Add("cpu-demand", "opera/legis/cassandra", operaModel.GetCpuDemand("select 0", "Database"));
        theMetrics.Add("cpu-demand", "opera/legis/load-balancer", operaModel.GetCpuDemand("select 0", "LBServer"));
        theMetrics.Add("cpu-demand", "opera/legis/web-workers", operaModel.GetCpuDemand("select 0", "WebServer"));
        theMetrics.Add("cpu-demand", "opera/legis/spark-workers", operaModel.GetCpuDemand("select 0", "AnalyticServer1"));
        theMetrics.Add("cpu-demand", "opera/legis/spark-virtual", operaModel.GetCpuDemand("select 0", "AnalyticServer2"));

        ArrayList<Integer> newDeployment = new ArrayList<Integer>();
        Double sparkContainersCnt = theMetrics.Get("spark-containers-cnt", "legis");
        Double webContainerCnt = theMetrics.Get("web-containers-cnt", "legis");
        Double webCPUUtil = theMetrics.GetAverage("docker.cpu-utilization", "legis/legis.web-worker.*") / 100;
        Double analyticCPUUtil = theMetrics.GetAverage("docker.cpu-utilization", "legis/legis.spark-worker.*") / 100;
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
        if (webCPUUtil < webCPULowUtil) {
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
        if (analyticCPUUtil < analyticCPULowUtil) {
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

        return newDeployment;
    }

    public void calibrateModel(MetricCollection theMetrics) {
        Double cpuLBUtil = theMetrics.Get("docker.cpu-utilization", "legis/legis.load-balancer") / 100;
        Double cpuWebUtil = theMetrics.GetAverage("docker.cpu-utilization", "legis/legis.web-worker.*") / 100;
        Double cpuAnalyticUtil = theMetrics.GetAverage("docker.cpu-utilization", "legis/legis.spark-worker.*") / 100;
        Double cpuDBUtil = theMetrics.GetAverage("docker.cpu-utilization", "legis/cassandra.*") / 100;
        Double throughput = theMetrics.Get("throughput", "legis/legis.load-balancer/find-routes") / 1000;
        Double respTime = theMetrics.Get("response-time", "legis/legis.load-balancer/find-routes");
        Double cntUsers = theMetrics.Get("count-users", "legis/legis.load-balancer/find-routes");
        Double sparkContainersCnt = theMetrics.Get("spark-containers-cnt", "legis");
        Double webContainerCnt = theMetrics.Get("web-containers-cnt", "legis");

        operaModel.SetNodeMultiplicity("WebHost", webContainerCnt.intValue());
        operaModel.SetNodeMultiplicity("AnalyticHost1", sparkContainersCnt.intValue());
        operaModel.SetPopulation("select 0", cntUsers);
        operaModel.solve();

        double[] measuredMetrics = {cpuLBUtil, cpuWebUtil, cpuAnalyticUtil, cpuDBUtil, respTime, throughput};
        EstimationResults res = theEstimator.EstimateModelParameters(measuredMetrics);
        System.out.println(res);
    }

    public static void main(String[] args) throws FileNotFoundException, UnsupportedEncodingException {
        OperaInterface oi = new OperaInterface("./input/BigDataApp.model.pxl", "./input/BigDataApp.kalman.config", "./input/measured_metrics.txt",
                "15", OperaInterface.THINK_TIME, 80, 5, OperaInterface.NO_SCENARIOS, "./output/FinalModel.pxl");
        //oi.trainModel();
        MetricCollection theMetrics = new MetricCollection();
        oi.calibrateModel(theMetrics);
        oi.getContainersCnt(theMetrics, 20.0, 80.0, 20.0, 80.0);
    }
}
