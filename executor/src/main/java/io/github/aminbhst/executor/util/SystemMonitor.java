package io.github.aminbhst.executor.util;

import org.springframework.stereotype.Component;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;

@Component
public class SystemMonitor {

    private final CentralProcessor processor;
    private final GlobalMemory memory;
    private long[] prevTicks;

    public SystemMonitor() {
        SystemInfo si = new SystemInfo();
        processor = si.getHardware().getProcessor();
        prevTicks = processor.getSystemCpuLoadTicks();
        memory = si.getHardware().getMemory();
    }

    public double getCpuLoad() {
        long[] currTicks = processor.getSystemCpuLoadTicks();
        double load = processor.getSystemCpuLoadBetweenTicks(prevTicks);
        prevTicks = currTicks;
        return load;
    }

    public int getPhysicalCores() {
        return processor.getPhysicalProcessorCount();
    }

    public int getLogicalCores() {
        return processor.getLogicalProcessorCount();
    }

    public long getTotalMemory() {
        return memory.getTotal();
    }

    public long getFreeMemory() {
        return memory.getAvailable();
    }

    public long getUsedMemory() {
        return memory.getTotal() - memory.getAvailable();
    }
}
