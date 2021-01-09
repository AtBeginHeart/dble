package com.actiontech.dble.log.general;


import com.actiontech.dble.log.RotateLogStore;
import com.actiontech.dble.server.status.GeneralLog;
import com.actiontech.dble.services.FrontEndService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GeneralLogProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(GeneralLogProcessor.class);
    private static final GeneralLogProcessor INSTANCE = new GeneralLogProcessor();
    private GeneralLogDisruptor logDelegate;
    private RotateLogStore.LogFileManager logFileManager;
    private volatile boolean enable = false;

    public static GeneralLogProcessor getInstance() {
        return INSTANCE;
    }

    private GeneralLogProcessor() {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                close();
            }
        });
    }

    // 处理方面：
    // 1.【委托】日志生产+队列+日志消费(日志包装->(缓冲区+落盘))；===== 模式：多个生产者+多个消费者 =====
    // 2.【文件管理】日志文件生成+日志文件翻滚

    // （当开启general_log时）重新初始化【委托】、【文件管理】

    public void start() {
        try {
            enable();
        } catch (Exception e) {
            LOGGER.warn("start general log failed, exception ：{}", e);
        }
    }

    public synchronized void enable() throws Exception {
        if (!enable) {
            this.logFileManager = RotateLogStore.getInstance().createFileManager(GeneralLog.getInstance().getGeneralLogFile(), GeneralLog.getInstance().getGeneralLogFileSize(), GeneralLog.getInstance().getGeneralLogBufferSize());
            this.logDelegate = new GeneralLogDisruptor(logFileManager, GeneralLog.getInstance().getGeneralLogQueueSize());
            this.logDelegate.start();
            enable = true;
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("enable general log success");
            }
        }
    }

    public synchronized void disable() {
        if (enable) {
            if (logDelegate != null) {
                logDelegate.stop();
            }
            if (logFileManager != null) {
                logFileManager.close();
            }
            enable = false;
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("disable general log success");
            }
        }
    }

    private void close() {
        if (enable) {
            disable();
            LOGGER.info("close general log file");
        }
    }

    public void putGeneralLog(FrontEndService service, String type, String sql) {
        if (enable && GeneralLog.getInstance().isEnableGeneralLog()) {
            GeneralLogEntry entry = new GeneralLogEntry(service.getConnection().getId(), type, sql);
            if (!logDelegate.tryEnqueue(entry)) {
                handleQueueFull(entry);
            }
        }
    }

    public void putGeneralLog(long connID, String type, String sql) {
        if (enable && GeneralLog.getInstance().isEnableGeneralLog()) {
            GeneralLogEntry entry = new GeneralLogEntry(connID, type, sql);
            if (!logDelegate.tryEnqueue(entry)) {
                handleQueueFull(entry);
            }
        }
    }

    public void putGeneralLog(FrontEndService service, byte[] data) {
        if (enable && GeneralLog.getInstance().isEnableGeneralLog()) {
            GeneralLogEntry entry = new GeneralLogEntry(service.getConnection().getId(), data, service.getCharset().getClient());
            if (!logDelegate.tryEnqueue(entry)) {
                handleQueueFull(entry);
            }
        }
    }

    public void putGeneralLog(String content) {
        if (enable && GeneralLog.getInstance().isEnableGeneralLog()) {
            GeneralLogEntry entry = new GeneralLogEntry(content);
            if (!logDelegate.tryEnqueue(entry)) {
                handleQueueFull(entry);
            }
        }
    }

    public boolean isEnable() {
        return enable;
    }

    private void handleQueueFull(final GeneralLogEntry entry) {
        LOGGER.warn("handleQueueFull.......");
        logDelegate.justEnqueue(entry);
    }
}
