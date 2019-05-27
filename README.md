# Top - A Toys Orchestrating Platform
## Introduction
### Abstract
Top is an implementation of [Toy](https://github.com/NugasDSL/toy). Toy imposes a kind of synchronization on the system 
because  a server can propose a block only in its turn. This method may limit the performance because the CPU
is ideal while waiting for the proposer's block. Top address the problem by running multiple instances of Toy 
(named _channels_) on the same underlying platforms instance. 
Top can achieve hundreds of thousands of transactions per second.
### System Model
As Toy, Top also assumes a Byzantine partial synchronous environment in which the number of faulty nodes, _f_ is less then third of the nodes.
_Partial synchronous_ means that after an unknown time _t_ there is an unknown upper bound _k_ on the messages transfer delays.

### Papers
This work is an implementation of the TOY algorithm described in [https://arxiv.org/abs/1901.03279](https://arxiv.org/abs/1901.03279)

## Getting Started
### Installation (for Ubuntu)
1. Install [Maven](https://maven.apache.org/):
    * `sudo apt install maven` 
1. Download Toy:
    * `git pull https://github.com/NugasDSL/toy.git`.
    * Or via this [link](https://github.com/NugasDSL/toy/archive/master.zip).
1. Download Top
    * `https://github.com/NugasDSL/top.git`.
    * Or via this [link](https://github.com/NugasDSL/top/archive/master.zip).
1. Go to the project directory and run:
    * `./install.sh`
1. `bin/top-server-main.jar` and `top_server.sh` will be created under the project directory.
1. `lib/top-core-1.0.jar` will be created under the project directory.

### Configurations
An example configuration can be found under `src/main/resources/`
#### bft-SMaRt Configuration
Top uses Toy that uses bft-SMaRt as an underlying platform in three different modules (_bbc_, _panic_ and _sync_). Hence, Top configuration should include
three different configuration directories - each one for each module. `src/main/resources/bbc`, `src/main/resources/panic` and `src/main/resources/sync` are samples for
such configurations with a single node.

Deeper explanation of bft-SMaRt configuration can be found [here](https://github.com/bft-smart/library/wiki/BFT-SMaRt-Configuration)
#### Toy Configuration
Top configuration is consist of a single `config.toml` file that describes Top's settings as well as paths to bft-SMaRt's configurations.

More about Top's configuration can be found in the [WiKi](https://github.com/NugasDSL/top/wiki/Configuration).

### Logging
You are supplied with the `src/main/resources/log4j.properties` that configures Top's logger. You may change it as you 
wish to adjust the logging level and the logger output to your needs.
### Server
#### Run a Server
The default configuration (supplied in `src/main/resources`) establishes a single server cluster.
To run the server type:
```
./bin/toy_server.sh $ID $PORT 
```
where `$ID` is the server id and `$PORT` is the port to which the client should submit its requests.

Note that you may pass as a third parameter a different `path/to/config.toml` then the default 
`src/main/resources/config.toml`

#### Server CLI
Command | Description
--------|------------
help | print the CLI description
init | initiate the server including its bft-SMaRt sub-platform
serve| start serving clients requests
stop | stop serving client requests and stop participating the algorithm
quit | exit the CLI
### Deploy a Client
You can deploy your own client using the IDL we defined in Toy. We also created a simple client in 
`src/main/java/top/client/TopClient.java`. The client exposes two simple methods: 
```
Types.accepted addTx(byte[] data) 
Types.approved getTx(Types.read r)
```
(this implementation is much alike the one demonstrated in Toy).

## Documentation
1. [javadoc](https://nugasdsl.github.io/top/apidocs/overview-summary.html)
2. [Paper](https://arxiv.org/abs/1901.03279)
