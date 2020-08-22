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


aws configure set default.region us-east-1
export ACCOUNT_ID=$(aws sts get-caller-identity --output text --query Account)
export AWS_REGION=$(curl -s 169.254.169.254/latest/dynamic/instance-identity/document | jq -r '.region')
echo -e " * * \e[106m ...AWS_REGION... : "$AWS_REGION"\e[0m \n"
echo -e " * * \e[106m ...ACCOUNT_ID... : "$ACCOUNT_ID"\e[0m \n"
aws configure set default.region us-east-1


## Creating a Key for ssh 
mkdir ~/environment/temp_ssh_keys
ssh-keygen -t rsa -N "" -f ~/environment/temp_ssh_keys/$PROJECT_NAME-sshkey.key

echo -e " * * \e[106m ...The Key Name to be created is... : "$PROJECT_NAME-sshkey"\e[0m"

### aws ec2 delete-key-pair --key-name $PROJECT_NAME-sshkey        // Take care while using this command, as it will delete the old keypair
aws ec2 import-key-pair --key-name $PROJECT_NAME-sshkey --public-key-material file://~/environment/temp_ssh_keys/$PROJECT_NAME-sshkey.key.pub

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


my_IP="$(curl http://checkip.amazonaws.com 2>/dev/null)" ; echo $my_IP
## Note your Public IP using a browser: http://checkip.amazonaws.com/

###Deploy the CFN Stack to create MSK Cluster and ES Cluster. <br/>
### (This CFN is inspired from : https://amazonmsk-labs.workshop.aws/en/mskkdaflinklab/overview.html) 
####This stack adds a new Windows instance in public subnet for accessing the kibana dashboard


##!!! This assumes CAPABILITY_NAMED_IAM as allowed for CloudFormation
## Refer https://docs.aws.amazon.com/cli/latest/reference/cloudformation/create-stack.html for more details.

aws cloudformation create-stack --stack-name $CFN_TEMPLATE_NAME --template-body file://~/environment/vk-analytics-examples/01-MSK-Producers-KDAFlink-ES/resources/cfn-msk.yaml --parameters ParameterKey=KeyName,ParameterValue=$PROJECT_NAME-sshkey ParameterKey=SSHLocation,ParameterValue=$my_IP/32 --capabilities CAPABILITY_NAMED_IAM



```
## Testing the MSK Simple Producer and Consumer commands


























