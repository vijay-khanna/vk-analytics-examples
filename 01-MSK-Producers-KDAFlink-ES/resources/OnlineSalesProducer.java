import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;


import kafka.javaapi.producer.Producer;
import kafka.producer.KeyedMessage;
import kafka.producer.ProducerConfig;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.concurrent.ThreadLocalRandom;

public class OnlineSalesProducer {
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
	//	FileReader fr=new FileReader("/tmp/sampleset.txt");    
    //    BufferedReader br=new BufferedReader(fr);    
		
		//setting up properties to be used to communicate to kafka
	Properties props = new Properties();
		props.put("metadata.broker.list", args[0].toString());
		props.put("serializer.class", "kafka.serializer.StringEncoder");
		props.put("request.required.acks", "1");
		ProducerConfig config = new ProducerConfig(props);
		Producer<String, String> producer = new Producer<String, String>(config);
		      
     
      
	int max = 4; 
		int min = 0;
		 String ItemsArray[] = {"Shirt", "Laptop","Keyboard", "Pen", "Mobile"};
		 String LocationArray[] = {"Pune", "Delhi","Mumbai", "Chennai", "Bangalore"};
		
		
		
	
		
	
			for (long nEvents = 1; nEvents != events; nEvents++) 
			{
			 int random_int = (int)(Math.random() * (max - min + 1) + min);
				 DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd.HH:mm:ss");  
				  LocalDateTime now = LocalDateTime.now();  
					// System.out.println(dtf.format(now));  
		 String datetimenow = dtf.format(now); 
				String msg = datetimenow+","+ItemsArray[random_int]+ ","+LocationArray[random_int]+","+((random_int*(random_int+1))+1)+","+(random_int+1)*10+"\n";
				
				System.out.print(msg);
				KeyedMessage<String, String> data = new KeyedMessage<String, String>(topics[0], new Date().getTime()+"", msg);
				producer.send(data);
				Thread.sleep(interval);
			}

	
		producer.close();
	}
}
