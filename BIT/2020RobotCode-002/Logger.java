package frc2020.util;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import edu.wpi.first.wpilibj.DriverStation;
import edu.wpi.first.wpilibj.Timer;

public class Logger {
    private final String LOG_PATH = "/home/lvuser/logs/";
    private final int CUT_AFTER = 3; // days
    private final int MAX_SIZE = 2*1024*1024; // bytes
    private final int MAX_NUMBER_OF_FILES = 100;
    private final DriverStation DS = DriverStation.getInstance();
    private UUID RUN_INSTANCE_UUID;

    private PrintWriter writer_;
    private boolean isStarted_ = false;
    private boolean fileLoggingDisabled_ = false;

    private static Logger instance_ = null;

    public static Logger getInstance() {
        if (instance_ == null) {
            instance_ = new Logger();
        }
        return instance_;
    }

    private Level LOGGER_LEVEL = Level.Debug;

    public enum Level {
        Debug(3), 
        Info(2), 
        Warning(1), 
        Error(0);

        private int level_int_;

        private Level(int level_int) {
            level_int_ = level_int;
        }

        public int getValue() {
            return level_int_;
        }
    }

    public void start(UUID runUuid, String logName, Level level) {
        LOGGER_LEVEL = level;
        if (isStarted_ || fileLoggingDisabled_) {
            return;
        }

        // Either create the logging directory or make sure it's not full

        try {
            RUN_INSTANCE_UUID = runUuid;
            Path path = Paths.get(LOG_PATH);
            if (!(Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))) {
                try {
                    Files.createDirectories(path);
                } catch (IOException e) {
                    DriverStation.reportError("Unable to create logs path. File logging not started", false);
                    setFileLogging(false);
                    return;
                }
            } else {
                if (pathSize(path) > MAX_SIZE || (getNumberOfFiles(path) > MAX_NUMBER_OF_FILES)) {
                    try {
                        Files.walk(Paths.get(LOG_PATH)).filter(Files::isRegularFile).map(Path::toFile)
                                .forEach(File::delete);
                    } catch (IOException e) {
                        DriverStation.reportError("Unable to clean log files path disabling file logging for safety",
                                false);
                        setFileLogging(false);
                        return;
                    }
                }
            }

            // Instantiate the writer and write the initial logging information

            writer_ = new PrintWriter(LOG_PATH + logName + "_" + RUN_INSTANCE_UUID.toString());
            writer_.println("==============Logger Start==============");
            writer_.println("Log Type: " + logName);
            writer_.println("Program Checksum: " + Checksum.getChecksum());
            writer_.println("Run UUID: " + RUN_INSTANCE_UUID);
            writer_.flush();
        } catch (IOException e) {
            DriverStation.reportError("Unable to start logger. Disabling file logging.", false);
            setFileLogging(false);
        }

        isStarted_ = true;
    }

    private synchronized void writeToLog(String msg) {
        if (fileLoggingDisabled_)
            return;
        if (!isStarted_) {
            throw new LoggerNotStartedException("The logger has not been started!");
        }
        writer_.println(msg);
    }

    public synchronized void logRobotInit() {
        writeToLog("=============Robot Init=============");
    }

    public synchronized void logRobotAutoInit() {
        writeToLog("==============Auto Start=============");
        if (DS.isFMSAttached()) {
            writeToLog("FMS Attached");
            writeToLog("Event Name: " + DS.getEventName());
            writeToLog("Match Type: " + DS.getMatchType());
            writeToLog("Match Number: " + DS.getMatchNumber());
            writeToLog("Alliance Color: " + DS.getAlliance());
            writeToLog("Driver Station Number: " + DS.getLocation());
        } else {
            writeToLog("FMS Not Attached");
        }
    }

    public synchronized void logRobotDisabled() {
        writeToLog("=============Robot Disabled=============");
    }

    public synchronized void logRobotTeleopInit() {
        writeToLog("=============Teleop Start============");
    }

    public synchronized void logRobotTestInit() {
        writeToLog("=============Test Start============");
    }

    public synchronized void close() {
        writeToLog("==============Logger End==============");
        writer_.close();
        isStarted_ = false;
    }

    private synchronized void logMessage(String msg, Level level) {
        if(level.getValue() == Level.Warning.getValue()) {
            DriverStation.reportWarning(msg, false);
        } 
        else if(level.getValue() == Level.Error.getValue()) {
            DriverStation.reportError(msg, false);
        } 
        else {
            System.out.println(level.toString() + " " + msg);
        }

        if (DS.isDSAttached()) {
            writeToLog("[" + level.toString() + "][" + LocalDateTime.now() + "] " + msg);
        } else {
            writeToLog("[" + level.toString() + "][" + Timer.getMatchTime() + "] " + msg);
        }
    }

    public void logDebug(String msg) {
        if (LOGGER_LEVEL.getValue() >= Level.Debug.getValue()) {
            logMessage(msg, Level.Debug);
        }
    }

    public void logInfo(String msg) {
        if (LOGGER_LEVEL.getValue() >= Level.Info.getValue()) {
            logMessage(msg, Level.Info);
        }
    }

    public void logWarning(String msg) {
        if (LOGGER_LEVEL.getValue() >= Level.Warning.getValue()) {
            logMessage(msg, Level.Warning);
        }
    }

    public void logError(String msg) {
        if (LOGGER_LEVEL.getValue() >= Level.Error.getValue()) {
            logMessage(msg, Level.Error);
        }
    }

    public synchronized void setFileLogging(boolean enabled) {
        fileLoggingDisabled_ = !enabled;
    }

    public synchronized void flush() {
        writer_.flush();
    }

    /**
     * Attempts to calculate the size of a file or directory.
     *
     * <p>
     * Since the operation is non-atomic, the returned value may be inaccurate.
     * However, this method is quick and does its best.
     * Thanks Stack Overflow
     */
    private long pathSize(Path path) {

        final AtomicLong size = new AtomicLong(0);

        try {
            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {

                    size.addAndGet(attrs.size());
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {

                    System.out.println("skipped: " + file + " (" + exc + ")");
                    // Skip folders that can't be traversed
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) {

                    if (exc != null)
                        System.out.println("had trouble traversing: " + dir + " (" + exc + ")");
                    // Ignore errors traversing a folder
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new AssertionError("walkFileTree will not throw IOException if the FileVisitor does not");
        }

        return size.get();
    }

    private long getNumberOfFiles(Path path){
        try {
            Stream<Path> files = Files.list(path);
            return files.count();
        } catch (IOException e) {
            System.out.println("Unable to determine number of files in " + path);
            return 0;
        }
    }
}