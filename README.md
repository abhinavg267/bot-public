This a [play-app](https://www.playframework.com/) written in Scala. There are three modules in the whole project. All of these are extensible and independent.
1. Strategy
2. Order Management
3. Broker

### Strategy
The project has an interface called `Strategy`, to create a new startegy for eg Short Straddle or Short Strangle, I have to extend this interface and rest is already handled.

### Order Management
The strategy is responsible to create an order in the system. The order management system then picks up this orders and try to send it to a broker, this handles failures, order splitting and everything else.

### Broker
The project again has an interface called `Broker`, to integrate a new broker, I simply need to extend this interface and implement a few methods, which will mostly be an api integration.

## Workflow
The project uses [Akka Actor System](https://doc.akka.io/docs/akka/current/typed/actors.html) to execute the strategy in a predefined interval of time. The project has a TaskScheduler to achieve the same. It executes each strategy every second.