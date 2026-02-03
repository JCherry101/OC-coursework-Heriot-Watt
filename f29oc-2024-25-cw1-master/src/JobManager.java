// v001 11/10/2024

	//	To implement the required concurrent functionality, your JobManager must use two Extrinsic Monitor classes:
	//			java.util.concurrent.locks.Condition;
	//			java.util.concurrent.locks.ReentrantLock;
	//	Note that you must not use the signalAll() method (as this creates inefficient polling activity).
	//
	//	No other thread-safe,  synchronised or scheduling classes or methods may be used. In particular:
	//	•	The keyword synchronized, and other classes from the package java.util.concurrent must be not be used. 
	//	•	Thread.Sleep() and any other methods that affect thread scheduling must not be used.
	//	•	“Busy waiting” techniques, such as spinlocks, must not be used. 
	//	Other non-thread-safe classes from SE17 may be used, e.g. LinkedLists, HashMaps and ArrayLists 
 	//	(these are unsynchronised and therefore not thread-safe).

    //See the Coursework spec for full list of constraints marking penalties

    import java.util.concurrent.locks.Condition;
    import java.util.concurrent.locks.ReentrantLock;
    import java.util.HashMap;
    import java.util.LinkedList;
    
    public class JobManager implements Manager {
        private final ReentrantLock lock = new ReentrantLock();
        private final LinkedList<JobRequest> job_queue = new LinkedList<>(); //list to store jobs in FIFO order
        private final HashMap<String, LinkedList<ServerInfo>> waitingServers = new HashMap<>(); //hash map,key is server and value is a list of waiting servers
    
        @Override
        public void specifyJob(JobRequest job) {
        	// Lock before modifying the job queue
            lock.lock();
            try {
            	// Put the new job in line
                job_queue.add(job); //adding single job for UR1
             // Attempt to assign servers right away if possible
                handleJobs();
            } finally {
            	//lock released for next thread 
                lock.unlock();
            }
        }
            
    
        @Override
        public String serverLogin(String type, int ID) {
        	// Lock before registering this server
            lock.lock();
            try {
            	// Track this server's details and await assignment
                ServerInfo SI = new ServerInfo(type, ID, lock.newCondition());
                waitingServers.computeIfAbsent(type, x -> new LinkedList<>()).add(SI);
                // Attempt to match any pending jobs
                handleJobs();
                // Wait until manager assigns a job
                while (!SI.isAssign()) {
                    SI.getCondition().awaitUninterruptibly();
                }
                // Return the assigned job name (or empty if none)
                return SI.getJobName() == null ? "" : SI.getJobName();
            } finally {
                lock.unlock();
            }
        }
    
        
        
        //==================================== PRIVATE METHODS & CLASSES  ===============================================
        
        private void handleJobs() {
            while (!job_queue.isEmpty() && canProcessNextJob()) {
                JobRequest currentJob = job_queue.poll();
                processJob(currentJob);
            }
        }
        
        private boolean canProcessNextJob() {
            JobRequest nextJob = job_queue.peek();
            if (nextJob == null) return false;
            
            // Check if we have enough servers of each type
            for (String type : nextJob.keySet()) {
                int requiredServers = nextJob.get(type);
                int availableServers = waitingServers.getOrDefault(type, new LinkedList<>()).size();
                if (availableServers < requiredServers) {
                    return false;
                }
            }
            return true;
        }
        
        private void processJob(JobRequest job) {
            // For each server type needed by the job
            for (String type : job.keySet()) {
                LinkedList<ServerInfo> servers = waitingServers.get(type);
                
                // Sort servers by ID in descending order if they are ComputeServers
                if (type.equals("ComputeServer")) {
                    sortServersById(servers);
                }
                
                // Allocate required number of servers
                int requiredServers = job.get(type);
                allocateServers(servers, requiredServers, job.jobName);
            }
        }
        
        private void sortServersById(LinkedList<ServerInfo> servers) {
            // Insertion sort in descending order
            for (int i = 1; i < servers.size(); i++) {
                ServerInfo current = servers.get(i);
                int j = i - 1;
                
                while (j >= 0 && servers.get(j).getId() < current.getId()) {
                    servers.set(j + 1, servers.get(j));
                    j--;
                }
                servers.set(j + 1, current);
            }
        }
        
        private void allocateServers(LinkedList<ServerInfo> servers, int count, String jobName) {
            for (int i = 0; i < count; i++) {
                ServerInfo server = servers.pollFirst();
                server.assignJob(jobName);
                server.getCondition().signal();
            }
        }
    
        private static class ServerInfo {
            private final String type;
            private final int jobID;
            private String jobName;
            private boolean assign;
            private final Condition condition;
    
            ServerInfo(String type, int id, Condition condition) {
                this.type = type;
                this.jobID = id;
                this.assign = false;
                this.condition = condition;
            }
            
            int getId() { 
                return jobID;
            }
            
            boolean isAssign() { 
                return assign; 
            }
            
            String getJobName() { 
                return jobName; 
            }
            
            Condition getCondition() {
                return condition;
            }
            
            void assignJob(String jobName) {
                this.jobName = jobName;
                this.assign = true;
            }
        }
    }
