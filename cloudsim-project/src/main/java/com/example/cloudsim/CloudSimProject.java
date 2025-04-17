package com.example.cloudsim;

import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;

import java.util.*;

public class CloudSimProject {

    public static void main(String[] args) {
        try {
            // Step 1: Initialize CloudSim
            CloudSim.init(1, Calendar.getInstance(), false);

            // Step 2: Create Datacenter
            Datacenter datacenter = createDatacenter("Datacenter_1");

            // Step 3: Use RoundRobinBroker instead of DatacenterBroker
            DatacenterBroker broker = new RoundRobinBroker("RoundRobinBroker");
            int brokerId = broker.getId();

            // Step 4: Create VMs with appropriate resources
            List<Vm> vmList = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                Vm vm = new Vm(i, brokerId, 1000, 1, 1024, 1000, 10000, // Reduced MIPS and RAM to fit within the host
                        "Xen", new CloudletSchedulerTimeShared());
                vmList.add(vm);
            }
            broker.submitVmList(vmList);

            // Step 5: Create Cloudlets (Tasks)
            List<Cloudlet> cloudletList = new ArrayList<>();
            UtilizationModel utilization = new UtilizationModelFull();
            for (int i = 0; i < 6; i++) {
                Cloudlet cloudlet = new Cloudlet(i, 40000, 1, 300, 300,
                        utilization, utilization, utilization);
                cloudlet.setUserId(brokerId);
                cloudletList.add(cloudlet);
            }

            // Step 6: Auto-scaling logic (adds VM if load > capacity)
            if (cloudletList.size() > vmList.size()) {
                Vm extraVm = new Vm(99, brokerId, 1500, 1, 1024, 2000, 15000, // Increased MIPS and RAM
                        "Xen", new CloudletSchedulerTimeShared());
                vmList.add(extraVm);
                broker.submitVmList(Collections.singletonList(extraVm));
                System.out.println("Auto-scaling: Extra VM added due to high load.");
            }

            // Step 7: Submit cloudlets (Round-robin scheduling will assign VMs)
            broker.submitCloudletList(cloudletList);

            // Step 8: Run simulation
            CloudSim.startSimulation();
            CloudSim.stopSimulation();

            // Step 9: Print results
            List<Cloudlet> results = broker.getCloudletReceivedList();
            printResults(results);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Datacenter createDatacenter(String name) {
        List<Host> hostList = new ArrayList<>();
        List<Pe> peList = new ArrayList<>();
        peList.add(new Pe(0, new PeProvisionerSimple(5000))); // Increased MIPS to 5000 to handle more VMs

        // Increased resources for the host
        Host host = new Host(0,
                new RamProvisionerSimple(8192), // 8 GB RAM
                new BwProvisionerSimple(10000), // 10 GB bandwidth
                5000000, // Increased MIPS to 5000 to handle more VMs
                peList,
                new VmSchedulerTimeShared(peList));
        hostList.add(host);

        DatacenterCharacteristics characteristics = new DatacenterCharacteristics(
                "x86", "Linux", "Xen", hostList,
                10.0, 3.0, 0.05, 0.001, 0.0);

        try {
            return new Datacenter(name, characteristics,
                    new VmAllocationPolicySimple(hostList),
                    new ArrayList<>(), 0);
        } catch (Exception e) {
            return null;
        }
    }

    private static void printResults(List<Cloudlet> list) {
        System.out.println("\n==== Cloudlet Execution Result ====");
        for (Cloudlet cloudlet : list) {
            System.out.printf("Cloudlet %d | VM %d | Time: %.2f | Start: %.2f | Finish: %.2f\n",
                    cloudlet.getCloudletId(),
                    cloudlet.getVmId(),
                    cloudlet.getActualCPUTime(),
                    cloudlet.getExecStartTime(),
                    cloudlet.getFinishTime());
        }
    }

    // Custom RoundRobinBroker class
    static class RoundRobinBroker extends DatacenterBroker {

        private int currentVmIndex = 0;

        public RoundRobinBroker(String name) throws Exception {
            super(name);
        }

        @Override
        public void submitCloudletList(List<? extends Cloudlet> list) {
            // Distribute the cloudlets in a round-robin fashion
            for (Cloudlet cloudlet : list) {
                // Assign each cloudlet to the next VM in the list
                cloudlet.setVmId(getVmList().get(currentVmIndex).getId());

                // Move to the next VM in the list, and loop back if we reach the end
                currentVmIndex = (currentVmIndex + 1) % getVmList().size();
            }

            // Submit the cloudlets to the broker
            super.submitCloudletList(list);
        }
    }
}