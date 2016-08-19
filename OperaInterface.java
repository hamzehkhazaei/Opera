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
 *
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


    public OperaInterface(String inputModel, String kalmanConfigFile, String metricsFile, String kalmanIterationCount, int thinkTime, int startLine,
                          int sampleNo, int noOfScenarios, String finalModel) throws FileNotFoundException, UnsupportedEncodingException {
        operaModel = new OperaModel();
        this.thinkTime = thinkTime;
        this.inputModel = inputModel;
        this.kalmanConfigFile = kalmanConfigFile;
        this.metricsFile = metricsFile;
        this.kalmanIterationCount = kalmanIterationCount;
        this.startLine = startLine;
        this.sampleNo = sampleNo;
        this.noOfScenarios = noOfScenarios;
        this.finalModel = finalModel;
    }

    private void trainModel() throws FileNotFoundException, UnsupportedEncodingException {
        operaModel.setModel(inputModel);

        KalmanConfiguration kalmanConfig = new KalmanConfiguration();
        kalmanConfig.withConfigFile(kalmanConfigFile)
                .withModel(operaModel)
                .withSetting(KalmanConfiguration.ITERATIONS_MAX, kalmanIterationCount);

        theEstimator = new KalmanEstimator(kalmanConfig);
        MeasuresUtil rm = new MeasuresUtil(metricsFile, startLine, sampleNo);

        ArrayList arrivals = rm.getArrivals();

        HashMap metrics = rm.getMetrics();

        ArrayList cpuLBUtil = (ArrayList) metrics.get("cpuLBUtil");
        ArrayList cpuWebUtil = (ArrayList) metrics.get("cpuWebUtil");
        ArrayList cpuAnalyticUtil = (ArrayList) metrics.get("cpuAnalyticUtil");
        ArrayList cpuDBUtil = (ArrayList) metrics.get("cpuDBUtil");
        ArrayList respTime = (ArrayList) metrics.get("respTime");
        ArrayList throughput = (ArrayList) metrics.get("throughput");

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
        operaModel.writer.close();

    }

    /*
     * this method returns the new number of web and analytic containers to bring the cpu utilization to the
     * desired range.
     */
    public ArrayList<Integer> getContainersCnt(MetricCollection theMetrics, Double webCPULowUtil, Double webCPUUPUtil,
                                               Double analyticCPULowUtil, Double analyticCPUUPUtil)
    {
        calibrateModel(theMetrics);

        ArrayList<Integer> newDeployment = new ArrayList<Integer>();
        Double sparkContainersCnt = theMetrics.Get("spark-containers-cn", "legis");
        Double webContainerCnt = theMetrics.Get("web-containers-cnt", "legis");
        Double webCPUUtil = theMetrics.GetAverage("docker.cpu-utilization", "legis/legis.web-worker*") / 100;
        Double analyticCPUUtil = theMetrics.GetAverage("docker.cpu-utilization", "legis/legis.spark-worker*") / 100;

        int webContNo = webContainerCnt.intValue();
        int analyticContNo = sparkContainersCnt.intValue();
        // to control the autonomic manager in case the Opera model did not converge.
        int controlCounter = 1;

        // if application under utilized; do nothing for now.
        if (webCPUUtil < webCPULowUtil || analyticCPUUtil < analyticCPULowUtil) {
            // do nothing for now.
            newDeployment.add(webContainerCnt.intValue());
            newDeployment.add(sparkContainersCnt.intValue());
            return newDeployment;
        }

        // if tomcat web server is over utilized scale it up one by one until bring it to the normal range.
        if (webCPUUtil > webCPUUPUtil) {
            while (operaModel.GetUtilizationContainer("WebContainer") > webCPUUPUtil && controlCounter < 5) {
                operaModel.SetNodeMultiplicity("WebHost", ++webContNo);
                operaModel.solve();
                controlCounter++;
            }
        }
        // if Spark cluster is over utilized scale it up one by one until bring it to the normal range.
        controlCounter = 1;
        if (analyticCPUUtil > analyticCPUUPUtil) {
            while (operaModel.GetUtilizationContainer("AnalyticContainer") > analyticCPUUPUtil && controlCounter < 5) {
                operaModel.SetNodeMultiplicity("WebHost", ++analyticContNo);
                operaModel.solve();
                controlCounter++;
            }
        }

        newDeployment.add(webContNo);
        newDeployment.add(analyticContNo);

        return newDeployment;
    }

    public void calibrateModel(MetricCollection theMetrics){
        Double cpuLBUtil = theMetrics.Get("docker.cpu-utilization", "legis/legis.load-balancer") /100;
        Double cpuWebUtil = theMetrics.GetAverage("docker.cpu-utilization", "legis/legis.web-worker*") /100;
        Double cpuAnalyticUtil = theMetrics.GetAverage("docker.cpu-utilization", "legis/legis.spark-worker*") / 100;
        Double cpuDBUtil = theMetrics.GetAverage("docker.cpu-utilization", "legis/cassandra.*") /100;
        Double throughput = theMetrics.Get("throughput", "legis/legis.load-balancer/find-routes") / 1000;
        Double respTime = theMetrics.Get("response-time", "legis/legis.load-balancer/find-routes");
        Double arrival = theMetrics.Get("count-users", "legis/legis.load-balancer/find-routes");

        operaModel.SetPopulation("select 0", arrival * (thinkTime + respTime));
        operaModel.solve();

        double[] measuredMetrics = {cpuLBUtil, cpuWebUtil, cpuAnalyticUtil, cpuDBUtil, respTime, throughput};
        theEstimator.EstimateModelParameters(measuredMetrics);
    }

    public static void main(String[] args) throws FileNotFoundException, UnsupportedEncodingException {
        OperaInterface oi = new OperaInterface("./input/BigDataApp.model.pxl", "./input/BigDataApp.kalman.config", "./input/metrics2.txt",
                "15", OperaInterface.THINK_TIME, 80, 5, OperaInterface.NO_SCENARIOS, "./output/FinalModel.pxl");
        oi.trainModel();
        MetricCollection theMetrics = new MetricCollection();
        oi.calibrateModel(theMetrics);
        oi.getContainersCnt(theMetrics, 20.0, 80.0, 20.0, 80.0);
    }
}
