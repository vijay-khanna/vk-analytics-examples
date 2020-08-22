This walkthrough will walkthrough : Create a MSK Cluster, Simple Producer/Consumer Commands, Custom Kakfa-Producer, Kinesis Analytics App using Flink, Push Data to Elastisearch and visualize in Kibana

This lab documentation is made for N.Virginia region (us-east-1). Please make note of this, and change accordingly for your deployment.

### Deploy Cloud9 IDE:
Login to the AWS EC2 Console, go to Cloud9 Services. <br/>
Create a **new environment** e.g. "Cloud9 Lab" <br/>
>#Select Environment type : "Create a new instance for environment (EC2)<br/>
>#Instance type : t2.micro (1 GiB RAM + 1 vCPU)  <br/>
>#Platform : Amazon Linux <br/>
>#Cost-saving setting: after 30 minutes <br/>
>#Network settings : Select an existing vpc and subnet, or create a new one . Select a Public Subnet, connected to Internet Gateway for Preview-URL Testing <br/>
>#Review, click Create <br/>

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
read -p "Enter a unique cluster Name (in plain-text, no special characters) : " PROJECT_NAME ; 
echo -e "\n * * \e[106m ...Project Name to be used is... : "$PROJECT_NAME"\e[0m \n"



## Some  House Keeping and Tools
rm -vf ${HOME}/.aws/credentials
sudo yum -y install jq gettext

export ACCOUNT_ID=$(aws sts get-caller-identity --output text --query Account)
export AWS_REGION=$(curl -s 169.254.169.254/latest/dynamic/instance-identity/document | jq -r '.region')
echo -e " * * \e[106m ...AWS_REGION... : "$AWS_REGION"\e[0m \n"
echo -e " * * \e[106m ...ACCOUNT_ID... : "$ACCOUNT_ID"\e[0m \n"


## Creating a Key for ssh 
mkdir ~/environment/temp_ssh_keys
ssh-keygen -t rsa -N "" -f ~/environment/temp_ssh_keys/$PROJECT_NAME-ssh-key.key








```

### Deploy MSK Stack:
```
# Clone the Repo 
cd ~/environment
## Remove existing Repo if exist. ###rm -rf ~/environment/vk-analytics-examples
git clone https://github.com/vijay-khanna/vk-analytics-examples.git


DATE_TODAY=`date +%Y-%m-%d`
CFN_TEMPLATE_NAME=$PROJECT_NAME-$DATE_TODAY ; echo $CFN_TEMPLATE_NAME
cd ~/environment/vk-analytics-examples/01-MSK-Producers-KDAFlink-ES/



```
1. Note your Public IP using a browser: http://checkip.amazonaws.com/

2. Deploy the CFN Stack to create MSK Cluster and ES Cluster. <br/>
(This CFN is inspired from : https://amazonmsk-labs.workshop.aws/en/mskkdaflinklab/overview.html) 
This stack adds a new Windows instance in public subnet for accessing the kibana dashboard

Specify the Stack name as "MSKFlinkES_KafkaCustomProducer"
Select a KeyName (if not exist, create a new one in the region, and save the private-key on laptop)
Specify the SSH location : use the PublicIP/32 i.e. "1.2.3.4/32"




























