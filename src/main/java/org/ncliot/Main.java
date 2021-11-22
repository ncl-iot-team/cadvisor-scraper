package org.ncliot;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.ncliot.metric.CPU;
import org.ncliot.metric.DiskIO;
import org.ncliot.metric.Memory;
import org.ncliot.metric.Network;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import org.bson.Document;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriBuilder;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Scrapes metrics of a Docker container using cAdvisor and pushes them to a mongoDB
 *
 * @author Ringo Sham
 */
public class Main {

    //Container ID to monitor - You have to find the container ID via the web frontend of cAdvisor or via Docker command line!
    //Change the ID HERE!
    private static final String containerId = "56ffd2cdbceda4eb85a2a26f6aad284aabad2c4a606b098a68ba32af40835cda";

    //MongoDB address
    //Change the host IP HERE!
    private static final String mongodbAddress = "mongodb://192.168.44.131:27017";

    //cAdvisor API endpoint
    //Change the host IP HERE!
    private static final String endpoint = "http://192.168.44.131:8080/api/v1.3/containers/docker/";

    //Temporary storage of metrics
    //Each entry in the map corresponds to 1 second of metric
    private static final Map<LocalDateTime, CPU> cpuMetrics = new HashMap<>();
    private static final Map<LocalDateTime, Memory> memoryMetrics = new HashMap<>();
    private static final Map<LocalDateTime, List<DiskIO>> diskMetrics = new HashMap<>();
    private static final Map<LocalDateTime, List<Network>> networkMetrics = new HashMap<>();

    public static void main(String[] args) throws InterruptedException {
        //A shorten form of the container ID
        String shortenId = containerId.substring(0, 8);

        //Setup REST Client
        ClientConfig config = new DefaultClientConfig();
        Client client = Client.create(config);

        //Setup MongoDB connection
        MongoClient mongoClient = MongoClients.create(mongodbAddress);
        System.out.println(("Connecting to " + mongodbAddress));

        //Print variables
        System.out.println("cAdvisor endpoint: " + endpoint);
        System.out.println("Container ID: " + containerId);

        //Create the database (will create one if it doesn't exist)
        MongoDatabase database = mongoClient.getDatabase("metrics");

        //This variable keeps track of the latest metric that we have already processed.
        LocalDateTime latestMetricTimestamp = null;

        //Pull metrics and push them into the database in a loop
        //noinspection InfiniteLoopStatement
        while (true) {
            //Setup request
            WebResource resource = client.resource(UriBuilder.fromUri(endpoint + containerId).build());
            System.out.println("Sending request to cAdvisor at " + LocalDateTime.now());
            //Send request
            ClientResponse response = resource.type(MediaType.APPLICATION_JSON_TYPE).get(ClientResponse.class);
            if (response.getStatus() == 500)
                throw new RuntimeException("Container " + containerId + " not found!");
            else if (response.getStatus() != 200)
                throw new RuntimeException(response.getEntity(String.class));
            //Get the response text
            String responseJson = response.getEntity(String.class);
            //Parse the Json using Gson
            Gson gson = new Gson();
            JsonObject root = gson.fromJson(responseJson, JsonObject.class);
            JsonArray stats = root.getAsJsonObject().get("stats").getAsJsonArray();

            //The metrics are already sorted by its timestamp. So by looping the array, we are looking at the metrics from oldest to newest
            for (JsonElement metric : stats) {
                LocalDateTime timestamp = LocalDateTime.parse(metric.getAsJsonObject().get("timestamp").getAsString(), DateTimeFormatter.ISO_DATE_TIME);
                if (latestMetricTimestamp != null) {
                    //If we have already processed this metric before, we skip
                    //This can happen because of network latency between the scraper and cAdvisor. We might see duplicate metrics between each metric request
                    if (timestamp.isBefore(latestMetricTimestamp) || timestamp.isEqual(latestMetricTimestamp))
                        continue;
                }
                latestMetricTimestamp = timestamp;

                //CPU metric
                JsonObject cpuJson = metric.getAsJsonObject().get("cpu").getAsJsonObject();
                long user = cpuJson.get("usage").getAsJsonObject().get("user").getAsLong();
                long system = cpuJson.get("usage").getAsJsonObject().get("system").getAsLong();
                long total = cpuJson.get("usage").getAsJsonObject().get("total").getAsLong();
                CPU cpu = new CPU(user, system, total);

                //Memory metric
                JsonObject memoryJson = metric.getAsJsonObject().get("memory").getAsJsonObject();
                long usage = memoryJson.get("usage").getAsLong();
                long maxUsage = memoryJson.get("max_usage").getAsLong();
                Memory memory = new Memory(usage, maxUsage);

                //DiskIO metric
                JsonObject diskioJson = metric.getAsJsonObject().get("diskio").getAsJsonObject();
                List<DiskIO> diskio = new ArrayList<>();
                //If there is no disk IO happening, this whole json object is empty.
                if (diskioJson.has("io_service_bytes")) {
                    int diskCount = diskioJson.get("io_service_bytes").getAsJsonArray().size();
                    //A container can have access to more than 1 drive. Hence, we need a list
                    for (int i = 0; i < diskCount; i++) {
                        String device = diskioJson.get("io_service_bytes").getAsJsonArray().get(i).getAsJsonObject().get("device").getAsString();
                        //Total disk io metrics in bytes
                        JsonObject byteMetrics = diskioJson.get("io_service_bytes").getAsJsonArray().get(i).getAsJsonObject().get("stats").getAsJsonObject();
                        //Total disk io metrics in disk operations
                        JsonObject opMetrics = diskioJson.get("io_serviced").getAsJsonArray().get(i).getAsJsonObject().get("stats").getAsJsonObject();
                        long byteRead = byteMetrics.get("Read").getAsLong();
                        long byteWrite = byteMetrics.get("Write").getAsLong();
                        long opRead = opMetrics.get("Read").getAsLong();
                        long opWrite = opMetrics.get("Write").getAsLong();
                        diskio.add(new DiskIO(device, byteRead, byteWrite, opRead, opWrite));
                    }
                } else {
                    System.out.println(("Warn: Theres are no disk IO metrics at " + timestamp));
                }

                //Network
                JsonObject networkJson = metric.getAsJsonObject().get("network").getAsJsonObject();
                //A container can have access to more than 1 network interface. Hence, we need a list
                List<Network> network = new ArrayList<>();
                if (networkJson.has("interfaces")) {
                    JsonArray netInterfaces = networkJson.getAsJsonArray("interfaces");
                    for (JsonElement netInterface : netInterfaces) {
                        String name = netInterface.getAsJsonObject().get("name").getAsString();
                        long rxBytes = netInterface.getAsJsonObject().get("rx_bytes").getAsLong();
                        long rxPackets = netInterface.getAsJsonObject().get("rx_packets").getAsLong();
                        long rxErrors = netInterface.getAsJsonObject().get("rx_errors").getAsLong();
                        long rxDropped = netInterface.getAsJsonObject().get("rx_dropped").getAsLong();
                        long txBytes = netInterface.getAsJsonObject().get("tx_bytes").getAsLong();
                        long txPackets = netInterface.getAsJsonObject().get("tx_packets").getAsLong();
                        long txErrors = netInterface.getAsJsonObject().get("tx_errors").getAsLong();
                        long txDropped = netInterface.getAsJsonObject().get("tx_dropped").getAsLong();
                        network.add(new Network(name, rxBytes, rxPackets, rxErrors, rxDropped, txBytes, txPackets, txErrors, txDropped));
                    }
                } else {
                    System.out.println(("Warn: Theres are no network metrics at " + timestamp));
                }

                //Store all info temporarily into a map
                cpuMetrics.put(timestamp, cpu);
                memoryMetrics.put(timestamp, memory);
                if (!diskio.isEmpty())
                    diskMetrics.put(timestamp, diskio);
                networkMetrics.put(timestamp, network);
            }

            //Prepare to write this current metric over the last minute to database
            //Prepare collections (Which are equivalent to tables in SQL)
            MongoCollection<Document> cpuCollection = database.getCollection(shortenId + "-cpu");
            MongoCollection<Document> memoryCollection = database.getCollection(shortenId + "-memory");
            //For disk and network, we create a new collection for each disk and each network interface
            //We find what disks are connected to the container by looking at one of the metrics in diskIO
            List<String> diskNames = new ArrayList<>();
            if (diskMetrics.keySet().iterator().hasNext()) {
                for (DiskIO disk : diskMetrics.get(diskMetrics.keySet().iterator().next()))
                    diskNames.add(disk.getDevice());
            }

            //Do the same for network interfaces
            List<String> networkInterfaces = new ArrayList<>();
            if (networkMetrics.keySet().iterator().hasNext()) {
                for (Network network : networkMetrics.get(networkMetrics.keySet().iterator().next()))
                    networkInterfaces.add(network.getName());
            }

            //Then create or get the mongo collections for those objects
            Map<String, MongoCollection<Document>> diskCollections = new HashMap<>();
            for (String diskName : diskNames)
                diskCollections.put(diskName, database.getCollection(shortenId + "-disk_" + diskName));
            Map<String, MongoCollection<Document>> networkCollections = new HashMap<>();
            for (String networkInterface : networkInterfaces)
                networkCollections.put(networkInterface, database.getCollection(shortenId + "-network_" + networkInterface));

            //Prepare Documents (Which are equivalent to entries in SQL)
            List<Document> cpuDocs = new ArrayList<>();
            for (LocalDateTime timestamp : cpuMetrics.keySet()) {
                CPU metric = cpuMetrics.get(timestamp);
                Document doc = new Document("timestamp", timestamp.toString())
                        .append("user", metric.getUser())
                        .append("system", metric.getSystem())
                        .append("total", metric.getTotal());
                cpuDocs.add(doc);
            }
            //Write all documents to database
            //We are doing an upsert, essentially the document is only inserted if it does not exist. Otherwise, the duplicate entry will be updated.
            cpuCollection.insertMany(cpuDocs);

            List<Document> memoryDocs = new ArrayList<>();
            for (LocalDateTime timestamp : memoryMetrics.keySet()) {
                Memory metric = memoryMetrics.get(timestamp);
                Document doc = new Document("timestamp", timestamp.toString())
                        .append("usage", metric.getUsage())
                        .append("max_usage", metric.getMaxUsage());
                memoryDocs.add(doc);
            }
            //Write all documents to database
            memoryCollection.insertMany(memoryDocs);

            for (String diskName : diskNames) {
                MongoCollection<Document> diskCollection = diskCollections.get(diskName);
                List<Document> diskioDocs = new ArrayList<>();
                for (LocalDateTime timestamp : diskMetrics.keySet()) {
                    List<DiskIO> metrics = diskMetrics.get(timestamp);
                    for (DiskIO metric : metrics) {
                        if (metric.getDevice().equals(diskName)) {
                            Document doc = new Document("timestamp", timestamp.toString())
                                    .append("byteRead", metric.getByteRead())
                                    .append("byteWrite", metric.getByteWrite())
                                    .append("opRead", metric.getByteRead())
                                    .append("opWrite", metric.getByteWrite());
                            diskioDocs.add(doc);
                            break;
                        }
                    }
                }
                //Write all documents to database
                diskCollection.insertMany(diskioDocs);
            }

            for (String networkInterface : networkInterfaces) {
                MongoCollection<Document> networkCollection = networkCollections.get(networkInterface);
                List<Document> networkDocs = new ArrayList<>();
                for (LocalDateTime timestamp : networkMetrics.keySet()) {
                    List<Network> metrics = networkMetrics.get(timestamp);
                    for (Network metric : metrics) {
                        if (metric.getName().equals(networkInterface)) {
                            Document doc = new Document("timestamp", timestamp.toString())
                                    .append("rxBytes", metric.getRxBytes())
                                    .append("rxPackets", metric.getRxPackets())
                                    .append("rxErrors", metric.getRxErrors())
                                    .append("rxDropped", metric.getRxDropped())
                                    .append("txBytes", metric.getTxBytes())
                                    .append("txPackets", metric.getTxPackets())
                                    .append("txErrors", metric.getTxErrors())
                                    .append("txDropped", metric.getTxDropped());
                            networkDocs.add(doc);
                            break;
                        }
                    }
                }
                //Write all documents to database
                networkCollection.insertMany(networkDocs);
            }

            //Each time we request cAdvisor, it returns metrics over the last minute.
            //We have to request every minute to get all metrics
            //Here we wait for 55 seconds. Accounting to latency

            //noinspection BusyWait
            Thread.sleep(55000);

            //Clear the map, and start over
            cpuMetrics.clear();
            memoryMetrics.clear();
            diskMetrics.clear();
            networkMetrics.clear();
        }
    }
}
