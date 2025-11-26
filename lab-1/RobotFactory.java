    import java.io.*;
import java.util.Properties;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class RobotFactory {
    // thread-safe counters
    private static final AtomicInteger skeletonCounter = new AtomicInteger(0);
    private static final AtomicInteger motorCounter = new AtomicInteger(0);
    private static final AtomicInteger robotCounter = new AtomicInteger(0);
    
    public static void main(String[] args) {
        Config config = loadConfig("config.properties");
        
        BlockingQueue<Skeleton> skeletonQueue = new LinkedBlockingQueue<>(10);
        BlockingQueue<Motor> motorQueue = new LinkedBlockingQueue<>(10);
        
        ExecutorService executor = Executors.newFixedThreadPool(3);
        
        // start producer and consumer threads
        executor.submit(new SkeletonProducer(skeletonQueue, config.skeletonFrequency));
        executor.submit(new MotorProducer(motorQueue, config.motorFrequency));
        executor.submit(new RobotAssembler(skeletonQueue, motorQueue, config.assemblyDuration));
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nShutting down factory...");
            executor.shutdownNow();
            printStatistics();
        }));
    }
    
    private static Config loadConfig(String filename) {
        Properties props = new Properties();
        try (InputStream input = new FileInputStream(filename)) {
            props.load(input);
            return new Config(
                Integer.parseInt(props.getProperty("skeleton.frequency", "1000")),
                Integer.parseInt(props.getProperty("motor.frequency", "1500")),
                Integer.parseInt(props.getProperty("assembly.duration", "2000"))
            );
        } catch (IOException e) {
            System.out.println("Config file not found, using defaults");
            return new Config(1000, 1500, 2000);
        }
    }
    
    private static void printStatistics() {
        System.out.println("\n=== Factory Statistics ===");
        System.out.println("Skeletons produced: " + skeletonCounter.get());
        System.out.println("Motors produced: " + motorCounter.get());
        System.out.println("Robots assembled: " + robotCounter.get());
    }
    
    static class Config {
        final int skeletonFrequency;
        final int motorFrequency;
        final int assemblyDuration;
        
        Config(int skeletonFreq, int motorFreq, int assemblyDur) {
            this.skeletonFrequency = skeletonFreq;
            this.motorFrequency = motorFreq;
            this.assemblyDuration = assemblyDur;
        }
    }
    
    static class Skeleton {
        final int id;
        Skeleton(int id) { this.id = id; }
    }
    
    static class Motor {
        final int id;
        Motor(int id) { this.id = id; }
    }
    
    static class Robot {
        final int id;
        final Skeleton skeleton;
        final Motor motor;
        
        Robot(int id, Skeleton skeleton, Motor motor) {
            this.id = id;
            this.skeleton = skeleton;
            this.motor = motor;
        }
    }
    
    // Producer thread for skeletons
    static class SkeletonProducer implements Runnable {
        private final BlockingQueue<Skeleton> queue;
        private final int frequency;
        
        SkeletonProducer(BlockingQueue<Skeleton> queue, int frequency) {
            this.queue = queue;
            this.frequency = frequency;
        }
        
        @Override
        public void run() {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    int id = skeletonCounter.incrementAndGet();
                    Skeleton skeleton = new Skeleton(id);
                    queue.put(skeleton);
                    System.out.println("[SKELETON] Produced skeleton #" + id);
                    Thread.sleep(frequency);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
    
    // Producer thread for motors
    static class MotorProducer implements Runnable {
        private final BlockingQueue<Motor> queue;
        private final int frequency;
        
        MotorProducer(BlockingQueue<Motor> queue, int frequency) {
            this.queue = queue;
            this.frequency = frequency;
        }
        
        @Override
        public void run() {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    int id = motorCounter.incrementAndGet();
                    Motor motor = new Motor(id);
                    queue.put(motor);
                    System.out.println("[MOTOR] Produced motor #" + id);
                    Thread.sleep(frequency);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
    
    // Consumer thread that assembles robots
    static class RobotAssembler implements Runnable {
        private final BlockingQueue<Skeleton> skeletonQueue;
        private final BlockingQueue<Motor> motorQueue;
        private final int assemblyDuration;
        
        RobotAssembler(BlockingQueue<Skeleton> skeletonQueue, 
                       BlockingQueue<Motor> motorQueue, 
                       int assemblyDuration) {
            this.skeletonQueue = skeletonQueue;
            this.motorQueue = motorQueue;
            this.assemblyDuration = assemblyDuration;
        }
        
        @Override
        public void run() {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    // take components from queues (blocks if empty)
                    Skeleton skeleton = skeletonQueue.take();
                    Motor motor = motorQueue.take();
                    
                    Thread.sleep(assemblyDuration); // simulate assembly time
                    
                    // create robot
                    int id = robotCounter.incrementAndGet();
                    Robot robot = new Robot(id, skeleton, motor);
                    
                    System.out.println("*** [ROBOT] Assembled robot #" + id + 
                                     " (skeleton #" + skeleton.id + 
                                     ", motor #" + motor.id + ") ***");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}