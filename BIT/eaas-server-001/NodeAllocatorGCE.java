/*
 * This file is part of the Emulation-as-a-Service framework.
 *
 * The Emulation-as-a-Service framework is free software: you can
 * redistribute it and/or modify it under the terms of the GNU General
 * Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version.
 *
 * The Emulation-as-a-Service framework is distributed in the hope that
 * it will be useful, but WITHOUT ANY WARRANTY; without even the
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
 * PARTICULAR PURPOSE.  See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with the Emulation-as-a-Software framework.
 * If not, see <http://www.gnu.org/licenses/>.
 */

package de.bwl.bwfla.eaas.cluster.provider.iaas;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.json.stream.JsonGenerator;
import javax.ws.rs.NotFoundException;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.batch.BatchRequest;
import com.google.api.client.googleapis.batch.json.JsonBatchCallback;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.googleapis.json.GoogleJsonErrorContainer;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.ComputeScopes;
import com.google.api.services.compute.model.AcceleratorConfig;
import com.google.api.services.compute.model.AccessConfig;
import com.google.api.services.compute.model.AttachedDisk;
import com.google.api.services.compute.model.AttachedDiskInitializeParams;
import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.MachineType;
import com.google.api.services.compute.model.Metadata;
import com.google.api.services.compute.model.Network;
import com.google.api.services.compute.model.NetworkInterface;
import com.google.api.services.compute.model.Operation;
import com.google.api.services.compute.model.Scheduling;

import de.bwl.bwfla.common.concurrent.SequentialExecutor;
import de.bwl.bwfla.common.logging.PrefixLogger;
import de.bwl.bwfla.common.logging.PrefixLoggerContext;
import de.bwl.bwfla.eaas.cluster.ClusterManagerExecutors;
import de.bwl.bwfla.eaas.cluster.NodeID;
import de.bwl.bwfla.eaas.cluster.ResourceSpec;
import de.bwl.bwfla.eaas.cluster.ResourceSpec.CpuUnit;
import de.bwl.bwfla.eaas.cluster.ResourceSpec.MemoryUnit;
import de.bwl.bwfla.eaas.cluster.config.NodeAllocatorConfigGCE;
import de.bwl.bwfla.eaas.cluster.dump.DumpConfig;
import de.bwl.bwfla.eaas.cluster.dump.DumpFlags;
import de.bwl.bwfla.eaas.cluster.dump.DumpHelpers;
import de.bwl.bwfla.eaas.cluster.dump.DumpTrigger;
import de.bwl.bwfla.eaas.cluster.dump.ObjectDumper;
import de.bwl.bwfla.eaas.cluster.provider.Node;
import de.bwl.bwfla.eaas.cluster.provider.NodeAllocationRequest;
import de.bwl.bwfla.eaas.cluster.provider.iaas.gce.ComputeOperationPollTrigger;
import de.bwl.bwfla.eaas.cluster.provider.iaas.gce.ComputeOperationWrapper;
import de.bwl.bwfla.eaas.cluster.provider.iaas.gce.ComputeRequestConstructor;
import de.bwl.bwfla.eaas.cluster.provider.iaas.gce.ComputeRequestTrigger;
import de.bwl.bwfla.eaas.cluster.provider.iaas.gce.ComputeRequestWrapper;


public class NodeAllocatorGCE implements INodeAllocator
{
	// Template variables
	public static final String TVAR_ADDRESS = "{{address}}";

	// NodeInfo metadata variables
	public static final String MDVAR_VMNAME = "vm_name";

	// Google API specific members
	protected final Compute gce;
	protected final Network network;
	protected final String machineTypeUrl;
	protected final String diskTypeUrl;

	protected final ComputeRequestWrapper.Initializer reqInitializer;
	protected final ComputeOperationWrapper.Initializer opInitializer;

	// Member fields
	protected final Logger log;
	protected final NodeAllocatorConfigGCE config;
	protected final NavigableMap<NodeID, NodeInfo> nodes;
	protected final NavigableSet<String> vmNameRegistry;
	protected final NodeNameGenerator vmNameGenerator;
	protected final NodeHealthChecker nodeHealthChecker;
	protected final ResourceSpec nodeCapacity;
	protected final Consumer<NodeID> onDownCallback;
	protected final ClusterManagerExecutors executors;
	protected final SequentialExecutor tasks;
	private final AtomicInteger numAllocationRequests;

	protected static final HttpTransport HTTP_TRANSPORT;

	static {
		try {
			HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
		}
		catch (Exception exception) {
			throw new IllegalStateException(exception);
		}
	}


	public NodeAllocatorGCE(NodeAllocatorConfigGCE config, Consumer<NodeID> onDownCallback,
			ClusterManagerExecutors executors, PrefixLoggerContext parentLogContext)
			throws GeneralSecurityException, IOException, InterruptedException, ExecutionException
	{
		final PrefixLoggerContext logContext = new PrefixLoggerContext(parentLogContext)
				.add("NA", "gce");

		// Initialize common member fields
		this.gce = NodeAllocatorGCE.newComputeService(config);
		this.log = new PrefixLogger(this.getClass().getName(), logContext);
		this.config = config;
		this.executors = executors;

		// Prepare the common settings for ComputeRequests
		this.reqInitializer = new ComputeRequestWrapper.Initializer()
				.setRetryInterval(config.getApiRetryInterval(), TimeUnit.MILLISECONDS)
				.setRetryIntervalDelta(config.getApiRetryIntervalDelta(), TimeUnit.MILLISECONDS)
				.setMaxNumRetries(config.getApiMaxNumRetries());

		// Prepare the common settings for ComputeOperations
		this.opInitializer = new ComputeOperationWrapper.Initializer()
				.setComputeService(gce)
				.setProjectId(config.getProjectId())
				.setRetryInterval(config.getApiPollInterval(), TimeUnit.MILLISECONDS)
				.setRetryIntervalDelta(config.getApiPollIntervalDelta(), TimeUnit.MILLISECONDS)
				.setUnlimitedNumRetries();

		// Submit async requests...
		final Future<Network> networkRequest = this.makeNetworkRequest();
		final Future<ResourceSpec> machineSpecRequest = this.makeMachineSpecRequest();

		// Initialize other member fields in the meantime...
		final String project = config.getProjectId();
		final String zone = config.getZoneName();
		this.nodes = new TreeMap<NodeID, NodeInfo>();
		this.vmNameRegistry = new ConcurrentSkipListSet<String>();
		this.vmNameGenerator = new NodeNameGenerator();
		this.onDownCallback = onDownCallback;
		this.tasks = new SequentialExecutor(log, executors.computation(), 64);
		this.numAllocationRequests = new AtomicInteger(0);
		this.machineTypeUrl = NodeAllocatorGCE.toTypeUrl(project, zone, "machineTypes/" + config.getVmType());
		this.diskTypeUrl = NodeAllocatorGCE.toTypeUrl(project, zone, "diskTypes/" + config.getVmPersistentDiskType());

		this.nodeHealthChecker = new NodeHealthChecker(
				Collections.unmodifiableCollection(nodes.values()),
				config,
				(nid) -> { onDownCallback.accept(nid); this.release(nid); },
				(delayed) -> this.scheduleHealthChecking(delayed),
				executors.io(),
				log);

		// Get the requested results...
		this.network = networkRequest.get();
		this.nodeCapacity = machineSpecRequest.get();

		this.scheduleHealthChecking(true);
	}


	/* ========== INodeAllocator Implementation ========== */

	@Override
	public ResourceSpec getNodeCapacity()
	{
		return nodeCapacity;
	}

	@Override
	public ResourceSpec allocate(NodeAllocationRequest request)
	{
		if (request.getSpec() == null)
			throw new IllegalArgumentException("No ResourceSpec specified!");

		if (request.getOnUpCallback() == null || request.getOnErrorCallback() == null)
			throw new IllegalArgumentException("No callbacks specified!");

		final long startTimestamp = System.currentTimeMillis();

		// Compute the number of VM instances to start...
		final int numRequestedNodes = NodeAllocatorUtil.computeNumRequiredNodes(request.getSpec(), nodeCapacity);
		final ResourceSpec pending = ResourceSpec.create(numRequestedNodes, nodeCapacity);

		final Runnable task = () -> {
			final AllocationResultHandler result =
					new AllocationResultHandler(numRequestedNodes, pending, request.getOnErrorCallback(), log);

			// Functor for waiting until the node is reachable
			final Function<NodeInfo, CompletableFuture<NodeInfo>> checkReachabilityFtor = (info) -> {
				log.info("Waiting for node '" + info.getNodeId() + "' to become reachable...");
				final NodeReachabilityPollTask check = new NodeReachabilityPollTask.Builder()
						.setPollInterval(config.getVmBootPollInterval(), TimeUnit.MILLISECONDS)
						.setPollIntervalDelta(config.getVmBootPollIntervalDelta(), TimeUnit.MILLISECONDS)
						.setMaxNumRetries(config.getVmMaxNumBootPolls())
						.setNodeAllocatorConfig(config)
						.setScheduler(executors.scheduler())
						.setExecutor(executors.io())
						.setLogger(log)
						.setNodeInfo(info)
						.build();

				executors.io().execute(check);
				return check.completion();
			};

			// Functor for handling success outcomes of boot requests
			final Consumer<NodeInfo> checkResultAction = (info) -> {
				final Node node = info.getNode();
				final NodeID nid = node.getId();
				if (node.isHealthy()) {
					log.info("Node '" + nid + "' is up and reachable");
					this.submit(() -> nodes.put(nid, info));
					result.onNodeReady(node.getCapacity());
					request.getOnUpCallback().accept(node);
				}
				else {
					final String message = "Node '" + nid + "' is unreachable after boot!";
					throw new CompletionException(new IllegalStateException(message));
				}

				long duration = System.currentTimeMillis() - startTimestamp;
				duration = TimeUnit.MILLISECONDS.toSeconds(duration);
				log.info("Allocating node '" + nid + "' took " + duration + " second(s)");
			};

			log.info("Starting " + numRequestedNodes + " requested node(s)...");

			// Submit all requests...
			for (int i = numRequestedNodes; i > 0; --i) {
				final String name = "vm-allocation-" + numAllocationRequests.incrementAndGet();
				final CleanupHandlerChain cleanups = new CleanupHandlerChain(name, log);

				// Functor for handling error outcome of a boot request
				final BiConsumer<Void, Throwable> checkErrorAction = (unused, error) -> {
					if (error == null)
						return;

					log.log(Level.WARNING, "Starting new VM failed!\n", error);
					result.onNodeFailure();
					cleanups.execute();
				};

				final CompletionTrigger<Void> trigger = new CompletionTrigger<Void>();
				this.makeVmInstanceInsertRequest(trigger.completion(), cleanups, request.getOnAllocatedCallback(), request.getUserMetaData())
					.thenCompose(checkReachabilityFtor)
					.thenAccept(checkResultAction)
					.whenComplete(checkErrorAction);

				trigger.submit(executors.io());
			}
		};

		executors.io().execute(task);
		return pending;
	}

	@Override
	public CompletableFuture<Boolean> release(NodeID nid)
	{
		if (nid == null)
			throw new IllegalArgumentException("Invalid node ID specified!");

		final CompletableFuture<Boolean> result = new CompletableFuture<Boolean>();

		final Runnable task = () -> {
			NodeInfo info = nodes.remove(nid);
			if (info == null) {
				result.complete(false);
				return;
			}

			final String name = (String) info.getMetadata().get(MDVAR_VMNAME);
			log.info("Releasing node '" + nid + "' (" + name + ")...");

			final CompletionTrigger<Void> trigger = new CompletionTrigger<Void>();
			this.makeVmInstanceDeleteRequest(trigger.completion(), info)
				.whenComplete((value, error) -> result.complete(value != null));

			trigger.submit(executors.io());
		};

		this.submit(task);
		return result;
	}

	@Override
	public boolean terminate()
	{
		final String project = config.getProjectId();
		final String zone = config.getZoneName();
		final BatchRequest batch = gce.batch();
		final NavigableSet<String> failedVmNames = new TreeSet<String>();

		final String vmDeleteBatchDescription = "batch of requests for deleting all VMs";
		log.info("Preparing " + vmDeleteBatchDescription + "...");
		if (vmNameRegistry.size() == 0) {
			log.info("No VMs found to be deleted");
			return true;
		}

		for (String vmname : vmNameRegistry) {
			try {
				final Compute.Instances.Delete request = gce.instances().delete(project, zone, vmname);
				final JsonBatchCallback<Operation> callback = new ShutdownBatchCallback(vmname, failedVmNames);
				batch.queue(request.buildHttpRequest(), Operation.class, GoogleJsonErrorContainer.class, callback);
			}
			catch (Exception exception) {
				log.log(Level.WARNING, "Building delete request for VM '" + vmname + "' failed!\n", exception);
				failedVmNames.add(vmname);
			}
		}

		final int numBatchedRequests = batch.size();
		if (numBatchedRequests > 0) {
			try {
				log.info("Sending " + vmDeleteBatchDescription + " to GCE...");
				batch.execute();
				log.info("Batch of " + numBatchedRequests + " delete request(s) sent");
			}
			catch (Exception exception) {
				log.log(Level.WARNING, "Sending " + vmDeleteBatchDescription + " failed!\n", exception);
			}
		}
		else {
			log.warning("Skipping sending an empty batch to GCE!");
		}

		final StringBuilder sb = new StringBuilder(2048);
		final int numFailedRequests = failedVmNames.size();
		final boolean batchWasExecuted = batch.size() != numBatchedRequests;
		if (batchWasExecuted && (numFailedRequests > 0)) {
			sb.append(numFailedRequests)
				.append(" out of ")
				.append(numBatchedRequests)
				.append(" delete request(s) failed!\n")
				.append('\n')
				.append("To retry manually, run:\n")
				.append("    $ gcloud compute instances delete \\\n");

			int counter = 0;
			final String spacer = "          ";
			for (String vmname : failedVmNames) {
				sb.append(spacer).append(vmname);
				if (++counter < numFailedRequests)
					sb.append(" \\\n");
			}

			sb.append(" \n");
			log.warning(sb.toString());
		}

		// VM summary message...
		{
			sb.setLength(0);

			final int numVmNamesPerLine = 4;
			int counter = 0;

			sb.append("Complete list of VMs managed by this node allocator:\n    ");
			for (String vmname : vmNameRegistry) {
				sb.append(vmname).append(' ');
				if (++counter == numVmNamesPerLine) {
					sb.append("\n    ");
					counter = 0;
				}
			}

			sb.append('\n');
			log.info(sb.toString());
		}

		return (batchWasExecuted && (numFailedRequests == 0));
	}

	@Override
	public void dump(JsonGenerator json, DumpConfig dconf, int flags)
	{
		final DumpTrigger trigger = new DumpTrigger(dconf);

		trigger.setSubResourceDumpHandler(() -> {
			final String segment = dconf.nextUrlSegment();
			switch (segment)
			{
				case "config":
					config.dump(json, dconf, flags);
					break;

				case "nodes":
					if (dconf.hasMoreUrlSegments()) {
						// Dump specific node...
						final String nid = dconf.nextUrlSegment();
						final NodeInfo node = nodes.get(new NodeID(nid));
						if (node == null)
							throw new NotFoundException("Node '" + nid + "' was not found!");

						node.dump(json, dconf, flags);
					}
					else {
						// Dump all nodes...
						json.writeStartArray();
						for (NodeInfo node : nodes.values())
							node.dump(json, dconf, flags);

						json.writeEnd();
					}

					break;

				default:
					DumpHelpers.notfound(segment);
			}
		});

		trigger.setResourceDumpHandler(() -> {
			final ObjectDumper dumper = new ObjectDumper(json, dconf, flags, this.getClass());

			dumper.add(DumpFields.CONFIG, () -> {
				json.writeStartObject(DumpFields.CONFIG);
				config.dump(json, dconf, flags | DumpFlags.INLINED);
				json.writeEnd();
			});

			dumper.add(DumpFields.NODE_CAPACITY, () -> {
				json.write(DumpFields.NODE_CAPACITY, DumpHelpers.toJsonObject(nodeCapacity));
			});

			dumper.add(DumpFields.NODES, () -> {
				json.write("num_" + DumpFields.NODES, nodes.size());
				json.writeStartArray(DumpFields.NODES);

				final int subflags = DumpFlags.reset(flags, DumpFlags.INLINED);
				for (NodeInfo node : nodes.values())
					node.dump(json, dconf, subflags);

				json.writeEnd();
			});

			dumper.add(DumpFields.VM_NAMES, () -> {
				json.write("num_" + DumpFields.VM_NAMES, vmNameRegistry.size());
				json.writeStartArray(DumpFields.VM_NAMES);
				vmNameRegistry.forEach((name) -> json.write(name));
				json.writeEnd();
			});

			dumper.run();
		});

		try {
			tasks.submit(trigger).get();
		}
		catch (Exception exception) {
			log.log(Level.WARNING, "Dumping internal state failed!", exception);
		}
	}

	private static class DumpFields
	{
		private static final String CONFIG         = "config";
		private static final String NODE_CAPACITY  = "node_capacity";
		private static final String NODES          = "nodes";
		private static final String VM_NAMES       = "vm_names";
	}


	/* ==================== Internal Helpers ==================== */

	private void submit(Runnable task)
	{
		tasks.execute(task);
	}

	private static String toTypeUrl(String project, String zone, String resource)
	{
		StringBuilder url = new StringBuilder(512)
				.append(Compute.DEFAULT_BASE_URL)
				.append(project)
				.append("/zones/")
				.append(zone)
				.append('/')
				.append(resource);

		return url.toString();
	}

	private static Compute newComputeService(NodeAllocatorConfigGCE config) throws GeneralSecurityException, IOException
	{
		final JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
		final Path keysPath = Paths.get(config.getServiceAccountCredentialsFile());
		final InputStream keysInputStream = Files.newInputStream(keysPath, StandardOpenOption.READ);
		GoogleCredential credential = GoogleCredential.fromStream(keysInputStream, HTTP_TRANSPORT, jsonFactory);
		if (credential.createScopedRequired()) {
			final List<String> scopes = new ArrayList<String>(1);
			scopes.add(ComputeScopes.COMPUTE);
			credential = credential.createScoped(scopes);
		}

		final Compute compute = new Compute.Builder(HTTP_TRANSPORT, jsonFactory, credential)
				.setApplicationName(config.getAppName())
				.build();

		return compute;
	}

	private Future<Network> makeNetworkRequest() throws IOException
	{
		final ScheduledExecutorService scheduler = executors.scheduler();
		final Executor executor = executors.io();
		final String project = config.getProjectId();
		final String netname = config.getNetworkName();

		// Functor definitions for the computation graph's steps...

		final Function<Void, CompletableFuture<Network>> netGetFtor = (unused) -> {
			final ComputeRequestWrapper<Network> request =
					new ComputeRequestWrapper.Builder<Network>(reqInitializer)
							.setRequest(() -> gce.networks().get(project, netname))
							.build();

			return ComputeRequestTrigger.submit(request, executor, scheduler);
		};

		final Function<Network, Network> netFoundFtor = (network) -> {
			log.info("Network '" + netname + "' found");
			return network;
		};

		// Compose the computation graph...
		final CompletionTrigger<Void> trigger = new CompletionTrigger<Void>();
		final CompletableFuture<Network> netReturnStage = trigger.completion()
				.thenCompose(netGetFtor)
				.thenApply(netFoundFtor);

		trigger.submit(executor);
		return netReturnStage;
	}

	private Future<ResourceSpec> makeMachineSpecRequest() throws IOException
	{
		final ScheduledExecutorService scheduler = executors.scheduler();
		final Executor executor = executors.io();

		// Functor definitions for the computation graph's steps...

		final Function<Void, CompletableFuture<MachineType>> machineGetFtor = (unused) -> {
			final ComputeRequestConstructor<MachineType> constructor = () -> {
				final String project = config.getProjectId();
				final String zone = config.getZoneName();
				final String type = config.getVmType();
				return gce.machineTypes().get(project, zone, type);
			};

			final ComputeRequestWrapper<MachineType> request =
					new ComputeRequestWrapper.Builder<MachineType>(reqInitializer)
							.setRequest(constructor)
							.build();

			return ComputeRequestTrigger.submit(request, executor, scheduler);
		};

		final Function<MachineType, ResourceSpec> machineToSpecFtor = (machine) -> {
			final int cpu = machine.getGuestCpus();
			final int memory = machine.getMemoryMb();
			final ResourceSpec spec = ResourceSpec.create(cpu, CpuUnit.CORES, memory, MemoryUnit.MEGABYTES);
			log.info("Using machine type '" + config.getVmType() + "' with spec: " + spec);
			return spec;
		};

		// Compose the computation graph...
		final CompletionTrigger<Void> trigger = new CompletionTrigger<Void>();
		final CompletableFuture<ResourceSpec> specReturnStage = trigger.completion()
				.thenCompose(machineGetFtor)
				.thenApply(machineToSpecFtor);

		trigger.submit(executor);
		return specReturnStage;
	}

	private CompletableFuture<NodeInfo> makeVmInstanceInsertRequest(CompletableFuture<Void> trigger,
			CleanupHandlerChain cleanups, Consumer<NodeID> onNodeAllocatedCallback, Function<String, String> userdata)
	{
		final ScheduledExecutorService scheduler = executors.scheduler();
		final Executor executor = executors.io();
		final String project = config.getProjectId();
		final String zone = config.getZoneName();
		final String rndsuffix = vmNameGenerator.next();
		final String vmname = config.getNodeNamePrefix() + rndsuffix;
		final String subdomain = config.getSubDomainPrefix() + rndsuffix;

		vmNameRegistry.add(vmname);
		cleanups.add(() -> vmNameRegistry.remove(vmname));

		// Functor definitions for the computation graph's steps...

		final Function<Void, CompletableFuture<Operation>> vmInsertFtor = (unused) -> {
			final ComputeRequestConstructor<Operation> constructor = () -> {
				final List<AttachedDisk> disks = new ArrayList<AttachedDisk>(2);
				final List<NetworkInterface> netifs = new ArrayList<NetworkInterface>(1);

				// Create boot disk
				{
					final AttachedDiskInitializeParams params = new AttachedDiskInitializeParams()
							.setDiskName(vmname)
							.setDiskType(diskTypeUrl)
							.setDiskSizeGb(config.getVmPersistentDiskSize())
							.setSourceImage(config.getVmPersistentDiskImageUrl());

					final AttachedDisk disk = new AttachedDisk()
							.setInitializeParams(params)
							.setType("PERSISTENT")
							.setMode("READ_WRITE")
							.setAutoDelete(true)
							.setBoot(true);

					disks.add(disk);
				}

				// Create network interface
				{
					final AccessConfig ac = new AccessConfig()
							.setName("external-nat")
							.setType("ONE_TO_ONE_NAT");

					final NetworkInterface netif = new NetworkInterface()
							.setAccessConfigs(Collections.singletonList(ac))
							.setNetwork(network.getSelfLink());

					netifs.add(netif);
				}

				final Metadata metadata = new Metadata()
						.setItems(new ArrayList<>());

				// Add user-defined metadata
				if (userdata != null) {
					final Metadata.Items item = new Metadata.Items()
							.setKey("user-data")
							.setValue(userdata.apply(subdomain));

					metadata.getItems().add(item);
				}

				// Create new VM instance
				final Instance instance = new Instance()
						.setName(vmname)
						.setMetadata(metadata)
						.setMachineType(machineTypeUrl)
						.setCanIpForward(false)
						.setNetworkInterfaces(netifs)
						.setDisks(disks);

				final String minCpuPlatform = config.getVmMinCpuPlatform();
				if (minCpuPlatform != null && !minCpuPlatform.isEmpty())
					instance.setMinCpuPlatform(minCpuPlatform);

				// Add guest accelerators...
				if (!config.getVmAccelerators().isEmpty()) {
					final List<AcceleratorConfig> accelerators = new ArrayList<AcceleratorConfig>();
					for (NodeAllocatorConfigGCE.AcceleratorConfig accelerator : config.getVmAccelerators()) {
						final String resource = "acceleratorTypes/" + accelerator.getType();
						final String type = NodeAllocatorGCE.toTypeUrl(project, zone, resource);
						final AcceleratorConfig ac = new AcceleratorConfig()
								.setAcceleratorCount(accelerator.getCount())
								.setAcceleratorType(type);

						accelerators.add(ac);
					}

					instance.setGuestAccelerators(accelerators);

					// The scheduling must also be modified!
					final Scheduling scheduling = new Scheduling()
							.setOnHostMaintenance("terminate");

					instance.setScheduling(scheduling);
				}

				return gce.instances().insert(project, zone, instance);
			};

			final ComputeOperationWrapper request = new ComputeOperationWrapper.Builder()
					.setRetryInterval(config.getVmBootPollInterval(), TimeUnit.MILLISECONDS)
					.setRetryIntervalDelta(config.getVmBootPollIntervalDelta(), TimeUnit.MILLISECONDS)
					.setMaxNumRetries(config.getVmMaxNumBootPolls())
					.setRequest(constructor)
					.setComputeService(gce)
					.setProjectId(project)
					.build();

			log.info("Creating VM '" + vmname + "'...");
			return ComputeOperationPollTrigger.submit(request, executor, scheduler);
		};

		final Function<Operation, CompletableFuture<Instance>> vmGetFtor = (operation) -> {
			log.info("VM '" + vmname + "' created");

			// Register a cleanup handler for VM instance
			{
				final Callable<CompletableFuture<Void>> handler = () -> {
					final ComputeOperationWrapper request =
							new ComputeOperationWrapper.Builder(opInitializer)
									.setRequest(() -> gce.instances().delete(project, zone, vmname))
									.build();

					log.info("Deleting VM '" + vmname + "'...");
					return ComputeOperationPollTrigger.submit(request, executor)
							.thenAccept((unused) -> log.info("VM '" + vmname + "' deleted"));
				};

				cleanups.add(handler);
			}

			log.info("Waiting for VM '" + vmname + "' to reach running state...");

			final ComputeRequestWrapper<Instance> request =
					new ComputeRequestWrapper.Builder<Instance>(reqInitializer)
							.setRequest(() -> gce.instances().get(project, zone, vmname))
							.setRetryInterval(config.getVmBootPollInterval(), TimeUnit.MILLISECONDS)
							.setRetryIntervalDelta(config.getVmBootPollIntervalDelta(), TimeUnit.MILLISECONDS)
							.setMaxNumRetries(config.getVmMaxNumBootPolls())
							.build();

			final Predicate<Instance> predicate = (instance) -> {
				return instance.getStatus().contentEquals("RUNNING");
			};

			return ComputeRequestTrigger.submit(request, predicate, executor, scheduler);
		};

		final Function<Instance, NodeInfo> vmInfoFtor = (instance) -> {
			// Lookup the public IP address of the instance
			final NetworkInterface netif = instance.getNetworkInterfaces().get(0);
			final AccessConfig ac = netif.getAccessConfigs().get(0);
			final String address = ac.getNatIP();
			if (address == null || address.isEmpty()) {
				Exception cause = new IllegalStateException("No external IP address for VM '" + vmname + "' found!");
				throw new CompletionException(cause);
			}

			log.info("VM '" + vmname + "' is running. Internal IP is " + netif.getNetworkIP() + ", external IP is " + address);

			// Signal that a machine is allocated
			final NodeID nid = new NodeID(address);
			try {
				nid.setSubDomainName(subdomain);
				onNodeAllocatedCallback.accept(nid);
			}
			catch (Exception error) {
				final Throwable cause = error.getCause();
				throw new CompletionException((cause != null) ? cause : error);
			}

			cleanups.add(() -> onDownCallback.accept(nid));

			// Compute the URL for health checks and create node
			final String urlTemplate = config.getHealthCheckUrl();
			final String healthCheckUrl = urlTemplate.replace(TVAR_ADDRESS, nid.getNodeAddress());
			final Node node = new Node(nid, nodeCapacity);
			try {
				final NodeInfo info = new NodeInfo(node, healthCheckUrl);
				info.getMetadata().put(MDVAR_VMNAME, vmname);
				return info;
			}
			catch (Exception exception) {
				throw new CompletionException(exception);
			}
		};

		// Compose the computation graph...
		return trigger.thenCompose(vmInsertFtor)
				.thenCompose(vmGetFtor)
				.thenApply(vmInfoFtor);
	}

	private CompletableFuture<NodeInfo> makeVmInstanceDeleteRequest(CompletableFuture<Void> trigger, NodeInfo info)
	{
		final ScheduledExecutorService scheduler = executors.scheduler();
		final Executor executor = executors.io();
		final String project = config.getProjectId();
		final String zone = config.getZoneName();
		final String vmname = (String) info.getMetadata().get(MDVAR_VMNAME);

		// Functor definitions for the computation graph's steps...

		final Function<Void, CompletableFuture<Operation>> vmDeleteFtor = (unused) -> {
			final ComputeOperationWrapper request =
					new ComputeOperationWrapper.Builder(opInitializer)
							.setRequest(() -> gce.instances().delete(project, zone, vmname))
							.build();

			log.info("Deleting VM '" + vmname + "'...");
			return ComputeOperationPollTrigger.submit(request, executor, scheduler);
		};

		final Function<Operation, NodeInfo> vmDeleteDoneFtor = (operation) -> {
			log.info("VM '" + vmname + "' deleted");
			vmNameRegistry.remove(vmname);
			return info;
		};

		// Compose the computation graph...
		return trigger.thenCompose(vmDeleteFtor)
				.thenApply(vmDeleteDoneFtor);
	}

	private void scheduleHealthChecking(boolean delayed)
	{
		// Action trigger...
		final Runnable trigger = () -> {
			this.submit(nodeHealthChecker);
		};

		if (delayed) {
			// Submit the action-trigger
			final long delay = config.getHealthCheckInterval();
			executors.scheduler().schedule(trigger, delay, TimeUnit.MILLISECONDS);
		}
		else {
			// Execute now!
			trigger.run();
		}
	}


	private static class ShutdownBatchCallback extends JsonBatchCallback<Operation>
	{
		private final NavigableSet<String> failedVmNames;
		private final String vmname;

		public ShutdownBatchCallback(String vmname, NavigableSet<String> failedVmNames)
		{
			this.failedVmNames = failedVmNames;
			this.vmname = vmname;
		}

		@Override
		public void onSuccess(Operation operation, HttpHeaders responseHeaders) throws IOException
		{
			// Nothing to do!
		}

		@Override
		public void onFailure(GoogleJsonError error, HttpHeaders responseHeaders) throws IOException
		{
			failedVmNames.add(vmname);
		}
	}
}
