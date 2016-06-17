## AWS VPC

Creates a VPC setup suitable for AWS Elastic Container Service.

The following are created:

* A non-default VPC, with CIDR 10.2.2.0/26, providing for up to 32 EC2 instances.
* Three or four subnets, one per availability zone - dependent on region.
* A public EC2 instance to act as a bastion, providing a single point of
ssh access to other EC2 instances.
* A single (initially) EC2 instance to host docker containers through ECS.
* Security groups, roles etc as required.

All resources created are identified by a single identifier, which enables
the creation and deletion of resources to be reliable, repeatable, and
idempotent. Idempotency provides for safe re-running of creation or deletion in
the event of transient problems in either creation or deletion, without any
creation of duplicate resources. The identifier needs to be unique for any
given AWS account.

Multiple independent VPCs can be created, up to the limit imposed by AWS
(by default, 5).

### Prerequesites

* JDK installed on desktop (tested with OpenJDK 1.8.0_91).

* AWS credentials in ~/.aws/credentials
  * The AWS account used needs administrator privileges.

* An SSH client and agent.

(Only tested on Kubuntu 16.04 LTS).

### Installation

#### JDK

<http://openjdk.java.net/install/>

#### AWS Credentials

<http://docs.aws.amazon.com/cli/latest/userguide/cli-chap-getting-started.html#cli-config-files>

Put a file called `credentials` in the `.aws` directory under your
home directory. This needs to contain credentials for an account or ideally an
IAM user that has permissions to read, write to and create AWS ECR repositories.
An example file would contain something like the following:

    [default]
    aws_access_key_id=BGHAU5SU23JJFO89QJEH
    aws_secret_access_key=huds8H/hudy73djdhwsuGYTFkugyuiItq/u8yHia

#### Gradle

The gradlew wrapper script will install Gradle as part of the build.

#### SSH

<http://www.openssh.com/>

### Usage

#### Building

A standalone jar is created by running

    ./gradlew shadowJar

from the project root.

#### Running

Creation of resources is done by running

    java -jar build/lib/create-vpc.jar <id>

from the project root directory, where `<id>` is some user-chosen identifier.
This will report progress to the console, and when complete will list the public
and private IP addresses of any EC2 instances created, plus commands for
connecting through SSH (on Linux anyway).

This operation is idempotent, so if it fails for any reason (e.g. connectivity,
defect, time-outs) it can be re-run safely.

Deletion of resources is done by running

    java -jar build/lib/create-vpc <id> delete

and again is idempotent and so can be run again if there are time-out issues.
Time-outs occasionally occur when deleting a set of resources that have only
recently been created.


<br/><hr/>

##### License

Copyright © 2016 Paul Whyler

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
