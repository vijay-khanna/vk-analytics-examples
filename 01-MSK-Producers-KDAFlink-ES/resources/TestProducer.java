import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;


import kafka.javaapi.producer.Producer;
import kafka.producer.KeyedMessage;
import kafka.producer.ProducerConfig;

public class TestProducer {
	public static void main(String[] args) throws InterruptedException, FileNotFoundException {
		if(args.length != 4)
		{
			System.out.println("Usage: java -cp KafkaProducerSample-0.0.1-SNAPSHOT-jar-with-dependencies.jar  <kafka-broker> <topics_seperated_by_comma> <num_of_events> <event_interval_in_ms>");
			System.exit(1);
		}
		
		//initializing variables
		long events = Long.parseLong(args[2]);
		Integer interval = Integer.parseInt(args[3]);
		String[] topics = args[1].split(",");
		System.out.println("Brokers : "+args[0]);
		System.out.println("Topic : "+args[1]);
		System.out.println("Number of Events : "+args[2]);
		System.out.println("Event Interval in ms :"+args[3]);
		
		///BufferedReader br = new BufferedReader(new InputStreamReader(TestProducer.class.getResourceAsStream("sampleset.txt")));
		FileReader fr=new FileReader("/tmp/sampleset.txt");    
        BufferedReader br=new BufferedReader(fr);    
		
		//setting up properties to be used to communicate to kafka
	Properties props = new Properties();
		props.put("metadata.broker.list", args[0].toString());
		props.put("serializer.class", "kafka.serializer.StringEncoder");
		props.put("request.required.acks", "1");
		ProducerConfig config = new ProducerConfig(props);
		Producer<String, String> producer = new Producer<String, String>(config);
		
		String line = "";
		String csvSplitBy = ",";
		Random rdm = new Random();
		ArrayList<List<String>> userdata = new ArrayList<>();
		
		try{
			//loading the data as Arrays of Lists to be able to pick different cells for generating random data from sample set
			while ((line = br.readLine()) != null) {
				String[] temp = line.split(csvSplitBy);
				userdata.add(Arrays.asList(temp));
			}
			//getting the size of the Array to use as random seed so that we wont run into ArrayIndexOutOfBounds Exception
			int size = userdata.size();

			//creating the data and sending it
			for (long nEvents = 1; nEvents != events; nEvents++) 
			{
				String msg = "{"+
						"\"StockSymbol\":"+userdata.get(rdm.nextInt(size)).get(0).toString().trim()+","+
						"\"StockNumber\":"+userdata.get(rdm.nextInt(size)).get(1).toString().trim()+","+
						"\"Mp\":"+userdata.get(rdm.nextInt(size)).get(2).toString().trim()+","+
						"\"Bp\":"+userdata.get(rdm.nextInt(size)).get(3).toString().trim()+","+
						"\"Ap\":"+userdata.get(rdm.nextInt(size)).get(4).toString().trim()+","+
						"\"BQ\":"+userdata.get(rdm.nextInt(size)).get(5).toString().trim()+","+
						"\"Aq\":"+userdata.get(rdm.nextInt(size)).get(6).toString().trim()+","+
						"\"Vol\":"+userdata.get(rdm.nextInt(size)).get(7).toString().trim()+","+
						"\"rowId\":"+userdata.get(rdm.nextInt(size)).get(8).toString().trim()
						+"}";
				
				System.out.println(msg);
				KeyedMessage<String, String> data = new KeyedMessage<String, String>(topics[0], new Date().getTime()+"", msg);
				producer.send(data);
				Thread.sleep(interval);
			}

		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (br != null) {
				try {
					br.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		producer.close();
	}
}
