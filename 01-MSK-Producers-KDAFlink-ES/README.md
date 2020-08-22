This walkthrough will walkthrough : Create a MSK Cluster, Simple Producer/Consumer Commands, Custom Kakfa-Producer, Kinesis Analytics App using Flink, Push Data to Elastisearch and visualize in Kibana

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
TAG_KEY_NAME="Project"
Cloud9_INSTANCE_ID="`wget -qO- http://instance-data/latest/meta-data/instance-id`" ; echo $Cloud9_INSTANCE_ID
REGION="`wget -qO- http://instance-data/latest/meta-data/placement/availability-zone | sed -e 's:\([0-9][0-9]*\)[a-z]*\$:\\1:'`"
PROJECT_NAME="`aws ec2 describe-tags --filters "Name=resource-id,Values=$INSTANCE_ID" "Name=key,Values=$TAG_NAME" --region $REGION --output=text | cut -f5`"
##echo $PROJECT_NAME
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
echo $(echo MSK_Bootstrap_servers=$MSK_Bootstrap_servers)



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


java -cp target/kafka-producer-app-1.0-SNAPSHOT.jar TestProdeucer $MSK_Bootstrap_servers 100 1000





### On KafkaClient Producer SSH Terminal
./kafka-topics.sh --zookeeper $MSK_Zookeeper --create --topic stock_topic --partitions 3 --replication-factor 3


```

## Advanced use case, Flink data to Elastisearch 
```
# Reference : https://amazonmsk-labs.workshop.aws/en/mskkdaflinklab/runproducer.html

```

### Prometheus and Kibana demo
```
# Reference : https://amazonmsk-labs.workshop.aws/en/openmonitoring/overview.html
```

### Cleanup
```
aws ssm delete-parameter --name MSK_Bootstrap_servers747
aws ssm delete-parameter --name MSK_Zookeeper747
aws ssm delete-parameter --name MSKClusterArn747

```
