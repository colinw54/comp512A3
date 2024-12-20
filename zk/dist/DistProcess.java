/*
Copyright
All materials provided to the students as part of this course is the property of respective authors. Publishing them to third-party (including websites) is prohibited. Students may save it for their personal use, indefinitely, including personal cloud storage spaces. Further, no assessments published as part of this course may be shared with anyone else. Violators of this copyright infringement may face legal actions in addition to the University disciplinary proceedings.
©2022, Joseph D’Silva; ©2024, Bettina Kemme
*/
import java.io.*;

import java.util.*;

// To get the name of the host.
import java.net.*;

//To get the process id.
import java.lang.management.*;

import org.apache.zookeeper.*;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.KeeperException.*;
import org.apache.zookeeper.data.*;

// TODO
// You may have to add other interfaces such as for threading, etc., as needed.
// This class will contain the logic for both your manager process as well as the worker processes.
//  Make sure that the callbacks and watch do not conflict between your manager's logic and worker's logic.
//		This is important as both the manager and worker may need same kind of callbacks and could result
//			with the same callback functions.
//	For simplicity, so far all the code in a single class (including the callbacks).
//		You are free to break it apart into multiple classes, if that is your programming style or helps
//		you manage the code more modularly.
//	REMEMBER !! Managers and Workers are also clients of ZK and the ZK client library is single thread - Watches & CallBacks should not be used for time consuming tasks.
//		In particular, if the process is a worker, Watches & CallBacks should only be used to assign the "work" to a separate thread inside your program.
public class DistProcess implements Watcher, AsyncCallback.ChildrenCallback
{
	ZooKeeper zk;
	String zkServer, pinfo;
	boolean isManager=false;
	boolean initialized=false;
	
	//List of workers if we are the manager
	private List<String> workers = new ArrayList<>();

	DistProcess(String zkhost)
	{
		zkServer=zkhost;
		pinfo = ManagementFactory.getRuntimeMXBean().getName();
		System.out.println("DISTAPP : ZK Connection information : " + zkServer);
		System.out.println("DISTAPP : Process information : " + pinfo);
	}

	void startProcess() throws IOException, UnknownHostException, KeeperException, InterruptedException
	{
		zk = new ZooKeeper(zkServer, 10000, this); //connect to ZK.
	}

	void initialize()
	{
		try
		{
			runForManager();	// See if you can become the manager (i.e, no other manager exists)
			isManager=true;
			getTasks(); // Install monitoring on any new tasks that will be created.
			// TODO monitor for worker tasks?
			monitorWorkers();

		}catch(NodeExistsException nee)
		{ 
			try {
			isManager=false; 
			//In the case that a manager already exists, Instead of doing nothing, register as a worker. This will be represented as an ephemeral znode under /dist20/workers
			registerAsWorker();
			} catch (Exception e){
				System.out.println("Unable to create worker due to an Exception");
			}
		
		} 
		catch(UnknownHostException uhe)
		{ System.out.println(uhe); }
		catch(KeeperException ke)
		{ System.out.println(ke); }
		catch(InterruptedException ie)
		{ System.out.println(ie); }

		System.out.println("DISTAPP : Role : " + " I will be functioning as " +(isManager?"manager":"worker"));

	}

	// Manager fetching task znodes...
	void getTasks()
	{
		zk.getChildren("/dist20/tasks", this, this, null);  
	}

	// Try to become the manager.
	void runForManager() throws UnknownHostException, KeeperException, InterruptedException
	{
		//Try to create an ephemeral node to be the manager, put the hostname and pid of this process as the data.
		// This is an example of Synchronous API invocation as the function waits for the execution and no callback is involved..
		zk.create("/dist20/manager", pinfo.getBytes(), Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
	}

	void registerAsWorker() throws UnknownHostException, KeeperException, InterruptedException
	{
		//Create a new workernode under /dist20/workers 
		String nodeName = zk.create("/dist20/workers/worker-", pinfo.getBytes(), Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT_SEQUENTIAL);
		
		//Create a new znode to monitor this worker's STATUS and INCOMING TASK?
		String workerMonitor = zk.create(nodeName + "/state", "idle".getBytes(), Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);

		System.out.println("New worker created: " + nodeName + " with a state at " + workerMonitor);
		

	}

	void monitorWorkers() {
		zk.getChildren("/dist20/workers", this, this, null);
	}

	public void process(WatchedEvent e)
	{
		//Get watcher notifications.

		//!! IMPORTANT !!
		// Do not perform any time consuming/waiting steps here
		//	including in other functions called from here.
		// 	Your will be essentially holding up ZK client library 
		//	thread and you will not get other notifications.
		//	Instead include another thread in your program logic that
		//   does the time consuming "work" and notify that thread from here.

		System.out.println("DISTAPP : Event received : " + e);

		if(e.getType() == Watcher.Event.EventType.None) // This seems to be the event type associated with connections.
		{
			// Once we are connected, do our intialization stuff.
			if(e.getPath() == null && e.getState() ==  Watcher.Event.KeeperState.SyncConnected && initialized == false) 
			{
				initialize();
				initialized = true;
			}
		}

		// Manager should be notified if any new znodes are added to tasks.
		if(e.getType() == Watcher.Event.EventType.NodeChildrenChanged && e.getPath().equals("/dist20/tasks"))
		{
			// There has been changes to the children of the node.
			// We are going to re-install the Watch as well as request for the list of the children.
			getTasks();
		} else if (e.getType() == Watcher.Event.EventType.NodeChildrenChanged && e.getPath().equals("/dist20/workers")) {
			//If there has been a change to the workers, re-install the watch
            monitorWorkers(); 
        }
	}

	//Asynchronous callback that is invoked by the zk.getChildren request.
	public void processResult(int rc, String path, Object ctx, List<String> children)
	{

		//!! IMPORTANT !!
		// Do not perform any time consuming/waiting steps here
		//	including in other functions called from here.
		// 	Your will be essentially holding up ZK client library 
		//	thread and you will not get other notifications.
		//	Instead include another thread in your program logic that
		//   does the time consuming "work" and notify that thread from here.

		// This logic is for manager !!
		//Every time a new task znode is created by the client, this will be invoked.

		// TODO: Filter out and go over only the newly created task znodes.
		//		Also have a mechanism to assign these tasks to a "Worker" process.
		//		The worker must invoke the "compute" function of the Task send by the client.
		//What to do if you do not have a free worker process?
		System.out.println("DISTAPP : processResult : " + rc + ":" + path + ":" + ctx);

		if (path.equals("/dist20/tasks")){

			if (children.isEmpty()) {
				return; // No tasks to assign
			}
			//Ensures it's processed sequentially
			Collections.sort(children);

			for(String task: children)
			{
				System.out.println(task);
				boolean isAssigned = false;
	
					//Attempt to assign the task to a free worker. If unsuccessful, wait a second, check for new workers, and re-try (this allows for new workers to come in)
					synchronized(workers) {
						for (String worker: workers){
							//Check if it's idle
							try {
								byte[] state = zk.getData("/dist20/workers/" + worker + "/state", false, null);
								
								if (new String(state).equals("idle")){
									isAssigned = true;
									byte[] taskInfo = zk.getData("/dist20/tasks/"+task, false, null);

									//Assign to the free worker and remove from task list
									zk.setData("/dist20/workers/" + worker + "/state", "busy".getBytes(), -1);
									zk.create("/dist20/workers/" + worker + "/task", taskInfo, Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
									zk.delete("/dist20/tasks/" + task, -1); 

									System.out.println("Task have been asssigned: " + task + " to worker: " + worker);
									break;
								}

							} catch (Exception e){
								System.out.println("There was a problem checking the current worker");
							}

						}
					}

					if (!isAssigned){
						System.out.println("No worker was available.");
						//TODO: Add mechanism for updating worker list. Maybe remove from tasks and add back?
					}





					/* 
					//TODO There is quite a bit of worker specific activities here,
					// that should be moved done by a process function as the worker.

					//TODO!! This is not a good approach, you should get the data using an async version of the API.
					byte[] taskSerial = zk.getData("/dist20/tasks/"+c, false, null);

					// Re-construct our task object.
					ByteArrayInputStream bis = new ByteArrayInputStream(taskSerial);
					ObjectInput in = new ObjectInputStream(bis);
					DistTask dt = (DistTask) in.readObject();

					//Execute the task.
					//TODO: Again, time consuming stuff. Should be done by some other thread and not inside a callback!
					dt.compute();
					
					// Serialize our Task object back to a byte array!
					ByteArrayOutputStream bos = new ByteArrayOutputStream();
					ObjectOutputStream oos = new ObjectOutputStream(bos);
					oos.writeObject(dt); oos.flush();
					taskSerial = bos.toByteArray();

					// Store it inside the result node.
					zk.create("/dist20/tasks/"+c+"/result", taskSerial, Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
					//zk.create("/dist20/tasks/"+c+"/result", ("Hello from "+pinfo).getBytes(), Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
					*/
				
				/* 
				catch(NodeExistsException nee){System.out.println(nee);}
				catch(KeeperException ke){System.out.println(ke);}
				catch(InterruptedException ie){System.out.println(ie);}
				catch(IOException io){System.out.println(io);}
				catch(ClassNotFoundException cne){System.out.println(cne);}
				*/
			}
		} else if (path.equals("/dist20/workers")){
			//If there has been a change in workers, update the list of workers that the manager has
			workers.clear();
			workers.addAll(children);
			System.out.println("The worker list has been updated: " + workers);
		}
	}

	public static void main(String args[]) throws Exception
	{
		//Create a new process
		//Read the ZooKeeper ensemble information from the environment variable.
		DistProcess dt = new DistProcess(System.getenv("ZKSERVER"));
		dt.startProcess();

		//Replace this with an approach that will make sure that the process is up and running forever.
		Thread.sleep(20000); 
	}
}
