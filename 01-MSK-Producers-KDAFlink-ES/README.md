This will walkthrough : Create a MSK Cluster, Simple Producer/Consumer Commands, Custom Kakfa-Producer, Kinesis Analytics App using Flink, Push Data to Elastisearch and visualize in Kibana

This lab documentation is made for N.Virginia region (us-east-1). Please make note of this, and change accordingly for your deployment.

### Deploy MSK Stack:
Better Option is to Deploy this Stack first, so that the Cloud9 Instance is created in same temporary VPC to allow access and Routing

* * Create a Temporary Keypair Manually before hand, and save the private-key.pem file . Mention this Private key for the CloudFormation Template. 

Deploy the CloudFormation Template : 

https://raw.githubusercontent.com/vijay-khanna/vk-analytics-examples/master/01-MSK-Producers-KDAFlink-ES/resources/MSKFlinkPrivateWithWinc9-3.yml


```
##**************** SKIP This *****************
##my_IP="$(curl http://checkip.amazonaws.com 2>/dev/null)" ; echo $my_IP
## Note your Public IP using a browser: http://checkip.amazonaws.com/
###echo "export C9_Public_IP=${my_IP}" >> ~/.bash_profile
###Deploy the CFN Stack to create MSK Cluster and ES Cluster. <br/>
### (This CFN is inspired from : https://amazonmsk-labs.workshop.aws/en/mskkdaflinklab/overview.html) 
####This stack adds a new Windows instance in public subnet for accessing the kibana dashboard
##!!! This assumes CAPABILITY_NAMED_IAM as allowed for CloudFormation
## Refer https://docs.aws.amazon.com/cli/latest/reference/cloudformation/create-stack.html for more details.
###aws cloudformation create-stack --stack-name $CFN_TEMPLATE_NAME --template-body file://~/environment/vk-analytics-examples/01-MSK-Producers-KDAFlink-ES/resources/cfn-msk.yaml --parameters ParameterKey=KeyName,ParameterValue=$PROJECT_NAME-sshkey ParameterKey=SSHLocation,ParameterValue=$my_IP/32 --capabilities CAPABILITY_NAMED_IAM
## This can take upto 15 minutes to setup. 
###aws cloudformation describe-stacks --stack-name $CFN_TEMPLATE_NAME
```

* **IAM AdministratorAccess Role for Cloud9 Instance :**
>#Go to IAM Service, create a role <br/>
>#Type of trusted entity : **AWS Service** <br/>
>#Choose the service that will use this role : **EC2**, 'Click Next:permissions' <br/>
>#Select from existing policies: **AdministratorAccess**, 'Next:Tags'  <br/>
>#Add-tags (optional) <br/>
>#Role Name: **Admin-Role_for_Cloud9_Instance** <br/>
>#Open EC2 Service console, select the Cloud9 Instance <br/>
>#Actions => Instance Settings => Attach/Replace IAM Role => Select **Admin-Role_for_Cloud9_Instance** => Apply<br/>

>#**In Cloud9 console => Preferences(Gear Icon on Upper Right Corner) => AWS Settings => Disable "AWS Managed Temporary Credentials. Slide the Grey button to cover Green area. Green area should not be visible** :relaxed:  <br/>


* **Capture a Unique Name for Project. This Name will be used to create a ssh key pair as well:**
```
## Read the Project Name from Instance Tag
export TAG_KEY_NAME="Project"
export TAG_NAME="Project"

Cloud9_INSTANCE_ID="`wget -qO- http://instance-data/latest/meta-data/instance-id`" ; echo $Cloud9_INSTANCE_ID
REGION="`wget -qO- http://instance-data/latest/meta-data/placement/availability-zone | sed -e 's:\([0-9][0-9]*\)[a-z]*\$:\\1:'`"
echo $REGION ; export REGION=$REGION
PROJECT_NAME="`aws ec2 describe-tags --filters "Name=resource-id,Values=$Cloud9_INSTANCE_ID" "Name=key,Values=$TAG_NAME" --region $REGION --output=text | cut -f5`"
echo $PROJECT_NAME
##read -p "Enter a unique cluster Name (in plain-text, no special characters) : " PROJECT_NAME ; 
echo -e "\n * * \e[106m ...Project Name to be used is... : "$PROJECT_NAME"\e[0m \n"

echo "export PROJECT_NAME=${PROJECT_NAME}" >> ~/.bash_profile

# Clone the Repo 
cd ~/environment
## Remove existing Repo if exist. ###rm -rf ~/environment/vk-analytics-examples
git clone https://github.com/vijay-khanna/vk-analytics-examples.git

##DATE_TODAY=`date +%Y-%m-%d`
##export CFN_TEMPLATE_NAME=$PROJECT_NAME-$DATE_TODAY ; echo $CFN_TEMPLATE_NAME
##echo "export CFN_TEMPLATE_NAME=${CFN_TEMPLATE_NAME}" >> ~/.bash_profile

## Some  House Keeping and Tools
rm -vf ${HOME}/.aws/credentials
sudo yum -y install jq gettext



export ACCOUNT_ID=$(aws sts get-caller-identity --output text --query Account)
export AWS_REGION=$(curl -s 169.254.169.254/latest/dynamic/instance-identity/document | jq -r '.region')
echo -e " * * \e[106m ...AWS_REGION... : "$AWS_REGION"\e[0m \n"
echo -e " * * \e[106m ...ACCOUNT_ID... : "$ACCOUNT_ID"\e[0m \n"
aws configure set default.region $AWS_REGION

echo "export AWS_REGION=${AWS_REGION}" >> ~/.bash_profile
echo "export ACCOUNT_ID=${ACCOUNT_ID}" >> ~/.bash_profile

## Creating a Key for ssh 
mkdir ~/environment/temp_ssh_keys

###ssh-keygen -t rsa -N "" -f ~/environment/temp_ssh_keys/$PROJECT_NAME-sshkey.key
####echo -e " * * \e[106m ...The Key Name to be created is... : "$PROJECT_NAME-sshkey"\e[0m"
###echo "export SSH_KEY=$PROJECT_NAME-sshkey" >> ~/.bash_profile

# Edit the Temporary Key Created Earlier, and Copy the content to the new file
# -----BEGIN RSA PRIVATE KEY-----
# to
# -----END RSA PRIVATE KEY-----



nano ~/environment/temp_ssh_keys/$PROJECT_NAME-sshkey.key





chmod 400 ~/environment/temp_ssh_keys/$PROJECT_NAME-sshkey.key

### aws ec2 delete-key-pair --key-name $PROJECT_NAME-sshkey        // Take care while using this command, as it will delete the old keypair
###aws ec2 import-key-pair --key-name $PROJECT_NAME-sshkey --public-key-material file://~/environment/temp_ssh_keys/$PROJECT_NAME-sshkey.key.pub


```




## Testing the MSK Simple Producer and Consumer commands
Reference to basic CRUD commands : https://amazonmsk-labs.workshop.aws/en/commontasks/kafkacrud.html
```
## Exporting some important variables

export MSKClusterArn=$(aws cloudformation describe-stacks --stack-name $PROJECT_NAME --query "Stacks[0].Outputs[?OutputKey=='MSKClusterArn'].OutputValue" --output text) ; echo $MSKClusterArn
echo "export MSKClusterArn=${MSKClusterArn}" >> ~/.bash_profile ; export MSKClusterArn=${MSKClusterArn} ; echo "MSKClusterArn=${MSKClusterArn}"


export MSK_Zookeeper=$(aws kafka describe-cluster --cluster-arn $MSKClusterArn --output json | jq ".ClusterInfo.ZookeeperConnectString") 
##echo "export MSK_Zookeeper=${MSK_Zookeeper}" >> ~/.bash_profile
echo "export MSK_Zookeeper=${MSK_Zookeeper//\"}" >> ~/.bash_profile ; export MSK_Zookeeper=${MSK_Zookeeper//\"}; echo MSK_Zookeeper=$MSK_Zookeeper



export MSK_Bootstrap_servers=$(aws kafka get-bootstrap-brokers --cluster-arn $MSKClusterArn --output json | jq ".BootstrapBrokerString") ; echo $MSK_Bootstrap_servers
##echo "export MSK_Bootstrap_servers=${MSK_Bootstrap_servers}" >> ~/.bash_profile
echo "export MSK_Bootstrap_servers=${MSK_Bootstrap_servers//\"}" >> ~/.bash_profile ; export MSK_Bootstrap_servers=${MSK_Bootstrap_servers//\"}; echo MSK_Bootstrap_servers=$MSK_Bootstrap_servers


#### * * * Add the Cloud9 Instance Security Group, as allowed in the Kafka client instance Security Group
#### * * * Add the Cloud9 Instance to Self Security Group, to allow all traffic from c9 to C9




##SSH to Kafka client
export KafkaClientEC2InstanceSsh=$(aws cloudformation describe-stacks --stack-name $PROJECT_NAME --query "Stacks[0].Outputs[?OutputKey=='KafkaClientEC2InstanceSsh'].OutputValue" --output text) ; echo $KafkaClientEC2InstanceSsh
KafkaClientEC2InstanceSsh="\"${KafkaClientEC2InstanceSsh}\""
echo $KafkaClientEC2InstanceSsh

echo "export KafkaClientEC2InstanceSsh=${KafkaClientEC2InstanceSsh}" >> ~/.bash_profile ; echo KafkaClientEC2InstanceSsh=$KafkaClientEC2InstanceSsh




## **** Open a new Terminal in cloud9 for ssh to Kafka Client     ##Will lead to KafkaClient_SSH_Terminal

$KafkaClientEC2InstanceSsh -i ~/environment/temp_ssh_keys/$PROJECT_NAME-sshkey.key
### !!!! This might have some issues, as Cloud9 can change the Public IP after reboot. In that case manually allow Cloud9's Public IP access to Kafka Client's Security Group.

### !!!! Copy paste the values of these commands from Cloud9 to the Kafka-Client-SSH Terminal.. this is one option.. or we can use Parameter Store
##echo -e " * * \e[106m echo export MSKClusterArn=$MSKClusterArn"\e[0m \n"
##echo -e " * * \e[106m ...ACCOUNT_ID... : "$ACCOUNT_ID"\e[0m \n"




##*****Cloud9_Terminal.. Copy Paste these Outputs to Kafka_Terminal

echo $(echo export MSKClusterArn=$MSKClusterArn)


echo $(echo export MSK_Zookeeper=$MSK_Zookeeper)


echo $(echo export MSK_Bootstrap_servers=$MSK_Bootstrap_servers)



### *** option - 2. 747 is just a unique identifier, could be any random number
# aws ssm put-parameter --name MSKClusterArn747 --type "String" --value  $MSKClusterArn
# aws ssm put-parameter --name MSK_Zookeeper747 --type "String" --value  $MSK_Zookeeper
# aws ssm put-parameter --name MSK_Bootstrap_servers747 --type "String" --value  $MSK_Bootstrap_servers
# aws ssm get-parameter --name "KafkaDemoMSKClusterArn747"


##****KafkaClient_SSH_Terminal

## Testing Kafka Cluster Basics. run these commands in SSH Terminal of KafkaClient.
cd ~/kafka/bin/
# Creating a New Topic and Deleting it
./kafka-topics.sh --zookeeper $MSK_Zookeeper --create --topic ExampleTopic --partitions 10 --replication-factor 3
./kafka-topics.sh --zookeeper $MSK_Zookeeper --create --topic ExampleTopic777 --partitions 10 --replication-factor 3
./kafka-topics.sh --zookeeper $MSK_Zookeeper --delete --topic ExampleTopic777
./kafka-topics.sh --zookeeper $MSK_Zookeeper --create --topic StockMarket --partitions 1 --replication-factor 3
./kafka-topics.sh --zookeeper $MSK_Zookeeper --create --topic OnlineSales --partitions 1 --replication-factor 3

##First_KAFKA_TERMINAL will work as  ProducerTerminal
# Producer..* * * To Write data to Brokers, using producer sample
./kafka-console-producer.sh --broker-list $MSK_Bootstrap_servers --topic ExampleTopic
### Will wait at > Prompt to accept messages, after the Consumer Teminal is Ready, we can enter messages here. 



# Consumer. * * * . //Run on another Terminal via SSH
$KafkaClientEC2InstanceSsh -i ~/environment/temp_ssh_keys/$PROJECT_NAME-sshkey.key    

## Copy Paste outputs from Cloud9 Terminal to the Consumer SSH Terminal of Kafla-Client. 
echo $(echo export MSKClusterArn=$MSKClusterArn)


echo $(echo export MSK_Zookeeper=$MSK_Zookeeper)


echo $(echo MSK_Bootstrap_servers=$MSK_Bootstrap_servers)



cd ~/kafka/bin/
./kafka-console-consumer.sh --bootstrap-server $MSK_Bootstrap_servers --topic ExampleTopic
## Whatever we type in Producer Console in > prompt, will be visible in Consumer Console ... 




```

### Kafka Producer, using Java JDK
```
# Reference for Maven Basic install : https://docs.aws.amazon.com/cloud9/latest/user-guide/sample-java.html
# update java to 1.8

## On Cloud 9 Terminal Console
sudo yum -y update

sudo yum -y install java-1.8.0-openjdk-devel

## Switch or upgrade the default Java development toolset to OpenJDK 8. To do this, run the update-alternatives command with the --config option. Run this command twice to switch or upgrade the command line versions of the Java runner and compiler.

sudo update-alternatives --config java      

sudo update-alternatives --config javac

# Validate
java -version
javac -version

mkdir ~/environment/kafka-producer ; cd ~/environment/kafka-producer

# Basic check of Java
cp ~/environment/vk-analytics-examples/01-MSK-Producers-KDAFlink-ES/resources/helloWorld.java .
javac helloWorld.java ; java helloWorld 5 9


# MVN Install
sudo wget http://repos.fedorapeople.org/repos/dchen/apache-maven/epel-apache-maven.repo -O /etc/yum.repos.d/epel-apache-maven.repo
sudo sed -i s/\$releasever/6/g /etc/yum.repos.d/epel-apache-maven.repo
sudo yum install -y apache-maven

mvn -version

# Create Structure
mvn archetype:generate -DgroupId=com.mycompany.app -DartifactId=kafka-producer-app -DarchetypeArtifactId=maven-archetype-quickstart -DinteractiveMode=false



cd ~/environment/kafka-producer/kafka-producer-app/
cp ~/environment/vk-analytics-examples/01-MSK-Producers-KDAFlink-ES/resources/sampleset.txt /tmp
cp ~/environment/vk-analytics-examples/01-MSK-Producers-KDAFlink-ES/resources/pom.xml .
cp ~/environment/vk-analytics-examples/01-MSK-Producers-KDAFlink-ES/resources/TestProducer.java ~/environment/kafka-producer/kafka-producer-app/src/main/java/com/mycompany/app/



mvn package

cd ~/environment/kafka-producer/kafka-producer-app/

java -cp target/KafkaProducerSample-0.0.1-SNAPSHOT-jar-with-dependencies.jar TestProducer $MSK_Bootstrap_servers StockMarket 100 1000

## Sample KDG App : https://github.com/avrsanjay/Kafka-Data-Generator





## Try Online Sales Kafka Sample : https://raw.githubusercontent.com/vijay-khanna/vk-analytics-examples/master/01-MSK-Producers-KDAFlink-ES/resources/OnlineSalesProducer.java
##java -cp target/KafkaProducerSample-0.0.1-SNAPSHOT-jar-with-dependencies.jar OnlineSalesProducer $MSK_Bootstrap_servers OnlineSales 100 1000






### On KafkaClient Producer SSH Terminal
##./kafka-topics.sh --zookeeper $MSK_Zookeeper --create --topic stock_topic --partitions 3 --replication-factor 3
cd ~/kafka/bin/
./kafka-console-consumer.sh --bootstrap-server $MSK_Bootstrap_servers --topic StockMarket


```
## Copy Data from Kafka to Kinesis FireHose (and then to S3)
```
1. Create a kinesis Firehose Delivery Stream, dump data to S3. 


2. Install the Connector Vector, Specify the Kafka BootStrap Server, and FireHose Stream name
## Ref : https://vector.dev/guides/integrate/sources/kafka/aws_kinesis_firehose/

curl --proto '=https' --tlsv1.2 -sSf https://sh.vector.dev | sh

3. Configure Vector
cat <<-VECTORCFG > vector.toml
[sources.in]
  bootstrap_servers = "10.14.22.123:9092,10.14.23.332:9092" # required
  group_id = "consumer-group-name" # required
  topics = ["^(prefix1|prefix2)-.+", "topic-1", "topic-2"] # required
  type = "kafka" # required

[sinks.out]
  # Encoding
  encoding.codec = "json" # required

  # General
  inputs = ["in"] # required
  region = "us-east-1" # required, required when endpoint = ""
  stream_name = "my-stream" # required
  type = "aws_kinesis_firehose" # required
VECTORCFG


### Replace the bootstrap server, and stream_name, region 
4. Start vector
vector --config vector.toml

5. Check in firehose monitoring and in S3 for logs. 

```


## Glue Crawler to store Table MetaData
```
#1
Create a glue Crawler, and point it to S3 bucket. 
run the Job. 

Check in some time in Athena for the data.

#2. Create Glue Job, Spark, Convert from JSON to Parquet. 
Create a new crawler, read Parquet data, create new Table


```


## Reading from CSV/Text on S3 to RDS using Sqoop
```
### REF : https://aws.amazon.com/blogs/big-data/use-sqoop-to-transfer-data-from-amazon-emr-to-amazon-rds/
## use the OnlineSalesProducer to dump data via kafka-kinesis-FH to S3
Create EMR cluster, ssh to cluster. 

su - hadoop

hive

DROP TABLE IF EXISTS OnlineSalesHive;

CREATE EXTERNAL TABLE OnlineSaleshive( 
transactiondate STRING,
Item STRING,
Location STRING,
ItemQty STRING,
ItemPrice STRING
)
ROW FORMAT DELIMITED FIELDS TERMINATED BY ','
LINES TERMINATED BY '\n'
STORED AS TEXTFILE
LOCATION 's3://aaa-kafka-vk747/rawfromkafka2020/';



select * from OnlineSalesHive;
select * from OnlineSaleshive where Location="Pune";

# create a RDS MySL DB, add necessary security groups and ports for access from EMR


mysql -u admin -h <>  -p

CREATE TABLE `OnlineSalesRds` (`transactiondate` varchar(20),`Item` varchar(20), `Location` varchar(20), `ItemQty` varchar(5), `ItemPrice` varchar(5) );

CREATE TABLE OnlineSalesRdsStaging LIKE OnlineSalesRds;


##on EMR SSH. Install the JDBC driver
wget http://dev.mysql.com/get/Downloads/Connector-J/mysql-connector-java-5.1.38.tar.gz
tar -xvzf mysql-connector-java-5.1.38.tar.gz
sudo cp mysql-connector-java-5.1.38/mysql-connector-java-5.1.38-bin.jar /usr/lib/sqoop/lib/


##Store encrypted passwords
hadoop credential create sqoop-blog-rds.password -provider jceks://hdfs/user/hadoop/sqoop-blog-rds.jceks


.
export
-Dhadoop.security.credential.provider.path=jceks://hdfs/user/hadoop/sqoop-blog-rds.jceks
--connect
jdbc:mysql://<<<RDS String/Database>>>
--username
admin
--password-alias
sqoop-blog-rds.password
--table
OnlineSalesRds
--staging-table
OnlineSalesRdsStaging
--fields-terminated-by
' '
--export-dir
s3://<Point to CSV Raw Folder, Which came from kinesis FH//>
.


sqoop --options-file options-file.txt


Check the data in RDS : select * from OnlineSalesRds;
```


## Advanced use case, Flink data to Elastisearch 
```
# Reference : https://amazonmsk-labs.workshop.aws/en/mskkdaflinklab/runproducer.html
## On Kinesis Client - Producer SSH Console

cd /tmp/kafka
nano producer.properties_msk
#BOOTSTRAP_SERVERS_CONFIG=$MSK_Bootstrap_servers    //Take output from cloud9 Console echo $SchemaRegistryPrivateDNS
#SCHEMA_REGISTRY_URL_CONFIG=SchemaRegistryPrivateDNS  // From CloudFormation Output


cd /tmp/kafka
nano schema-registry.properties
#kafkastore.bootstrap.servers = $MSK_Bootstrap_servers


sudo systemctl start confluent-schema-registry
sudo systemctl status confluent-schema-registry



/home/ec2-user/kafka/bin/kafka-topics.sh --create --zookeeper $MSK_Zookeeper --replication-factor 3 --partitions 3 --topic ExampleTopic
/home/ec2-user/kafka/bin/kafka-topics.sh --create --zookeeper $MSK_Zookeeper --replication-factor 3 --partitions 3 --topic Departments_Agg

/home/ec2-user/kafka/bin/kafka-topics.sh --create --zookeeper $MSK_Zookeeper --replication-factor 3 --partitions 3 --topic ClickEvents_UserId_Agg_Result

/home/ec2-user/kafka/bin/kafka-topics.sh --create --zookeeper $MSK_Zookeeper --replication-factor 3 --partitions 3 --topic User_Sessions_Aggregates_With_Order_Checkout



##cd /tmp/kafka
##java -jar KafkaClickstreamClient-1.0-SNAPSHOT.jar -t ExampleTopic -pfp /tmp/kafka/producer.properties_msk -nt 8 -rf 1800 
## https://github.com/vijay-khanna/vk-analytics-examples/blob/master/01-MSK-Producers-KDAFlink-ES/resources/KafkaClickstreamClient-1.0-SNAPSHOT.jar


## or Try Stocks-Topic on cliud9 Console
cd ~/environment/kafka-producer/kafka-producer-app/
java -cp target/KafkaProducerSample-0.0.1-SNAPSHOT-jar-with-dependencies.jar TestProducer $MSK_Bootstrap_servers stock_topic 100 1000
##https://github.com/vijay-khanna/vk-analytics-examples/blob/master/01-MSK-Producers-KDAFlink-ES/resources/KafkaProducerSample-0.0.1-SNAPSHOT-jar-with-dependencies.jar


## On kinesis Data Analytics Console, "Configure"
## Properties => Edit Group => "Update the BootStrap Servers and zooKeeper Values"
## Topic = stock_topic
## Monitoring => Enable CloudWatch => Warn
## VPC => Select MMVPC
## Security Group => KDA Security Group
## Select the Private Subnets 
## update..and wait 

##### Run the Application , click the "Run" button



```

### Prometheus and Kibana demo
```
### Some minor challenge still ~~~~ not working. 
# Reference : https://amazonmsk-labs.workshop.aws/en/openmonitoring/overview.html
# https://amazonmsk-labs.workshop.aws/en/openmonitoring/prep.html

### Enable "Enhanced topic-level monitoring", "Enable open monitoring with Prometheus", "Prometheus exporters = JMX EXporter and Node Exporter"

## Create a New Security Group "MSK_Monitoring", Allow All-Ports from this Security Gropup to Itself
## Add this Security Group to the Cloud9, and the KafkaClient instance


## on Cloud 9 Console
mkdir ~/environment/prometheus ; cd ~/environment/prometheus

cp ~/environment/vk-analytics-examples/01-MSK-Producers-KDAFlink-ES/resources/prometheus.yml .
cat ~/environment/prometheus/prometheus.yml



cp ~/environment/vk-analytics-examples/01-MSK-Producers-KDAFlink-ES/resources/targets.json .
temp_id="$( cut -d ',' -f 1 <<< "$MSK_Bootstrap_servers" )"; echo $temp_id
temp_id_bkp="$( cut -d ',' -f 1 <<< "$MSK_Bootstrap_servers" )"; echo $temp_id_bkp
temp_id="$( cut -d ':' -f 1 <<< "$temp_id" )"; echo "$temp_id"
temp_id="${temp_id:3}" ; echo $temp_id

temp_id_1=b-1$temp_id ; echo $temp_id_1
temp_id_2=b-2$temp_id ; echo $temp_id_2
temp_id_3=b-3$temp_id ; echo $temp_id_3

sed -e "s/broker_dns_1/${temp_id_1}/g" ~/environment/prometheus/targets.json > ~/environment/prometheus/targets.json.tmp && mv ~/environment/prometheus/targets.json.tmp ~/environment/prometheus/targets.json
sed -e "s/broker_dns_2/${temp_id_2}/g" ~/environment/prometheus/targets.json > ~/environment/prometheus/targets.json.tmp && mv ~/environment/prometheus/targets.json.tmp ~/environment/prometheus/targets.json
sed -e "s/broker_dns_3/${temp_id_3}/g" ~/environment/prometheus/targets.json > ~/environment/prometheus/targets.json.tmp && mv ~/environment/prometheus/targets.json.tmp ~/environment/prometheus/targets.json

cat ~/environment/prometheus/targets.json


# docker ps -a
# docker rm prometheus

sudo docker run -d -p 9090:9090 --name=prometheus -v /home/ec2-user/environment/prometheus/prometheus.yml:/etc/prometheus/prometheus.yml -v /home/ec2-user/environment/prometheus/targets.json:/etc/prometheus/targets.json prom/prometheus --config.file=/etc/prometheus/prometheus.yml


## open Security Group of Cloud9, for port 9090, 3000 to Your_public_IP/32
#Connect to Cloud9 Instance
#http://<Cloud9_Public_IP>:9090



```

### Cleanup
```
aws ssm delete-parameter --name MSK_Bootstrap_servers747
aws ssm delete-parameter --name MSK_Zookeeper747
aws ssm delete-parameter --name MSKClusterArn747

```
