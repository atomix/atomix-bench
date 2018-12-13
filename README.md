# Atomix Benchmarking Framework

This project provides a benchmarking framework for [Atomix 3](https://github.com/atomix/atomix). To build the framework, pull and build the project:

```
mvn clean package
```

Once the project is built, extract the `tar.gz` or `zip` archive:

```
tar -xvf target/atomix-bench.tar.gz -C atomix-bench && cd atomix-bench
```

## Setting up the benchmark

Benchmarking works by adding special benchmark executor nodes to a running Atomix cluster. Benchmark nodes are configured the same way as standard Atomix agent nodes, but their sole purpose is to run benchmarks. Multiple benchmark nodes can be added to partition benchmark operations among multiple clients.

To add a benchmark node to a running cluster, run the `atomix-bench agent` command:

```
./bin/atomix-bench agent -c bench.conf
```

Benchmarks are run by a _controller_ node which should be chosen from one of the benchmark nodes. When a benchmark is run, the controller node:
* Configures the benchmark
* Assigns operations to each benchmark node
* Records and reports the progress of the benchmark
* Aggregates benchmark results

To run a benchmark, use the `atomix-bench run` command, setting the `$ATOMIX_HOST` and/or `$ATOMIX_PORT` environment variables to the desired controller node:

```
ATOMIX_PORT=5678 ./bin/atomix-bench run map --operations 10000 --protocol.type multi-raft --protocol.group raft
```

The available benchmark types can be read from the help text provided by the `atomix-bench run` command:

```
ATOMIX_PORT=5678 ./bin/atomix-bench run -h
```

Additionally, the arguments supported by each benchmark type can be listed by specifying the benchmark type name:

```
ATOMIX_PORT=5678 ./bin/atomix-bench run map -h
```

Benchmark types are dynamically registered and are read from the REST API, so a benchmark controller node must be running and configured.

## Docker

To run the benchmark in Docker, first setup an Atomix cluster to benchmark if one is not running already:

```
echo 'cluster {
  discovery {
    type: bootstrap
    nodes.1 {
      id: atomix-1
      host: atomix-1
    }
    nodes.2 {
      id: atomix-2
      host: atomix-2
    }
    nodes.3 {
      id: atomix-3
      host: atomix-3
    }
  }
}

managementGroup {
  type: raft
  partitions: 1
  members: [atomix-1, atomix-2, atomix-3]
}

partitionGroups.raft {
  type: raft
  partitions: 3
  members: [atomix-1, atomix-2, atomix-3]
}' > agent.conf
```

```
docker run --name atomix-1 --net atomix -it -v $(pwd)/agent.conf:/opt/atomix-bench/agent.conf atomix/atomix:latest -c /opt/atomix-bench/agent.conf -m atomix-1 -a atomix-1 --data-dir /var/data/atomix-1
```

```
docker run --name atomix-2 --net atomix -it -v $(pwd)/agent.conf:/opt/atomix-bench/agent.conf atomix/atomix:latest -c /opt/atomix-bench/agent.conf -m atomix-2 -a atomix-2 --data-dir /var/data/atomix-2
```

```
docker run --name atomix-3 --net atomix -it -v $(pwd)/agent.conf:/opt/atomix-bench/agent.conf atomix/atomix:latest -c /opt/atomix-bench/agent.conf -m atomix-3 -a atomix-3 --data-dir /var/data/atomix-3
```

To run a benchmark node in Docker, use the `atomix/atomix-bench` Docker image:

```
echo 'cluster {
  discovery {
    type: bootstrap
    nodes.1 {
      id: atomix-1
      host: atomix-1
    }
    nodes.2 {
      id: atomix-2
      host: atomix-2
    }
    nodes.3 {
      id: atomix-3
      host: atomix-3
    }
  }
}' > bench.conf
```

```
docker run --name bench-1 --net atomix -it -v $(pwd)/bench.conf:/opt/atomix/bench.conf atomix/atomix-bench:latest agent -c /opt/atomix/bench.conf
```

Finally, run the benchmark via the benchmark node:

```
docker run -it --net atomix -e ATOMIX_HOST='bench-1' atomix/atomix-bench:latest run map --operations 10000 --concurrency 10 --deterministic --protocol.type multi-raft --protocol.group raft
```
