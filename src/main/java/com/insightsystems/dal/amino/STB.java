package com.insightsystems.dal.amino;

import com.avispl.symphony.api.dal.control.Controller;
import com.avispl.symphony.api.dal.dto.control.AdvancedControllableProperty;
import com.avispl.symphony.api.dal.dto.control.ControllableProperty;
import com.avispl.symphony.api.dal.dto.monitor.ExtendedStatistics;
import com.avispl.symphony.api.dal.dto.monitor.GenericStatistics;
import com.avispl.symphony.api.dal.dto.monitor.Statistics;
import com.avispl.symphony.api.dal.monitor.Monitorable;
import com.avispl.symphony.dal.communicator.TelnetCommunicator;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class STB extends TelnetCommunicator implements Controller, Monitorable {
    final String queryKernelRelease = "uname -r",queryCpuUsage = "iostat -c",queryNetwork = "ifconfig eth0", queryMemory = "cat /proc/meminfo",queryNumProcesses = "ps | wc -l";

    long lastRetrieveTime = 0L;
    long networkIn,networkOut;

    public STB(){
        this.setLoginPrompt("AMINET login: ");
        this.setPasswordPrompt("Password: ");
        this.setTimeout(10000);
        this.setCommandErrorList(Collections.singletonList("None"));
        this.setCommandSuccessList(Collections.singletonList(""));
        this.setLoginErrorList(new ArrayList<String>(){{add(getLoginPrompt());add(getPasswordPrompt());}});
        this.setLoginSuccessList(Collections.singletonList(""));
        this.setLogin("root");
        this.setPassword("root2root");
    }

    @Override
    public List<Statistics> getMultipleStatistics() throws Exception {
        ExtendedStatistics extStats = new ExtendedStatistics();
        GenericStatistics genStats = new GenericStatistics();
        Map<String, String> stats = new LinkedHashMap<>();
        if (!send("").contains("\u001B7\u001B8[root@AMINET]#")){
            Thread.sleep(200L);
            send("");
        }
        final float cpuIdle = Float.parseFloat(regexFind(send(queryCpuUsage), "avg-cpu.+[\\s\\n]+(?:\\d+\\.\\d+\\s+){5}(\\d+\\.\\d+)"));
        genStats.setCpuPercentage(100.0F - cpuIdle);

        genStats.setNumberOfProcesses(Integer.parseInt(regexFind(send(queryNumProcesses),"(\\d+)")));
        stats.put("kernelVersion",send(queryKernelRelease).split("\r\n")[1]);

        String memoryResponse = send(queryMemory);
        genStats.setMemoryTotal(Float.parseFloat(regexFind(memoryResponse,"MemTotal:\\s+(\\d+)"))*0.000001F);
        genStats.setMemoryInUse(Float.parseFloat(regexFind(memoryResponse,"MemFree:\\s+(\\d+)"))*0.000001F);

        String networkResponse = send(queryNetwork);
        if (regexFind(networkResponse,"RX bytes:\\s?(\\d+)").isEmpty()){
            networkResponse = send(queryNetwork);
        }
        long currentRx = Long.parseLong(regexFind(networkResponse,"RX bytes:\\s?(\\d+)"));
        long currentTx = Long.parseLong(regexFind(networkResponse,"TX bytes:\\s?(\\d+)"));
        stats.put("macAddress",regexFind(networkResponse,"HWaddr ([\\dA-F:]+)"));

        long now = System.currentTimeMillis();
        if (lastRetrieveTime != 0L){
            genStats.setNetworkIn((((float)(currentRx - networkIn))/1048576F) / (((float)(now-lastRetrieveTime))/1000F));
            genStats.setNetworkOut((((float)(currentTx - networkOut))/1048576F) / (((float)(now-lastRetrieveTime))/1000F));
        }
        lastRetrieveTime = now;
        networkIn = currentRx;
        networkOut = currentTx;

        stats.put("reboot","0");
        extStats.setControllableProperties(createControls());
        extStats.setStatistics(stats);
        return new ArrayList<Statistics>(){{add(extStats);add(genStats);}};
    }

    private List<AdvancedControllableProperty> createControls() {
        List<AdvancedControllableProperty> controls = new ArrayList<>();
        AdvancedControllableProperty.Button rebootButton = new AdvancedControllableProperty.Button();
        rebootButton.setLabel("Reboot");
        rebootButton.setLabelPressed("Rebooting...");
        rebootButton.setGracePeriod(10000L);
        controls.add(new AdvancedControllableProperty("reboot",new Date(),rebootButton,"0"));
        return controls;
    }

    @Override
    public void controlProperty(ControllableProperty cp) throws Exception {
        if (cp == null || cp.getProperty().isEmpty()){
            return;
        }

        if (cp.getProperty().equals("reboot")){
            if (cp.getValue().equals("1")) {
                send("reboot");
            }
        } else {
            if (logger.isDebugEnabled()){
                logger.debug("Control Property: " + cp.getProperty() + " was not found.");
            }
        }
    }

    @Override
    public void controlProperties(List<ControllableProperty> list) throws Exception {
        for (ControllableProperty cp : list){
            controlProperty(cp);
        }
    }

    private String regexFind(String sourceString,String regex){
        final Matcher matcher = Pattern.compile(regex).matcher(sourceString);
        return matcher.find() ? matcher.group(1) : "";
    }

    public static void main(String[] args) throws Exception {
        STB test = new STB();
        test.setHost("10.231.64.92");
        test.setLogin("root");
        test.setPassword("root2root");
        test.init();

        Map<String,String> stats = ((ExtendedStatistics)test.getMultipleStatistics().get(0)).getStatistics();
        System.out.println("Properties");
        stats.forEach((k,v)->System.out.println(k + " : " + v));
    }

}
