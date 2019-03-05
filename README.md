## The solution of the hero called Stefanos

Stefanos was among those that have underestimated the power of Antaeus. He promised to be an easy challenge for Stefanos, but during the battle it has been swiftly revealed that Antaeus has the upper hand. Stefanos had to react quickly and regroup. Luckily he came prepared and had a rope which he used to hang Antaeus by his ankles on a tree. To this day Antaeus is still hanging on that tree due to the lack of muscle on his abdomen.


### Timeline of the challenge

During pre-analysis, I have estimated that 6 days (getting to know Kotlin included) should be enough to implement the required service.

The first two days were about reading up on Kotlin and getting to know its differences and similarities compared to other languages I have encountered.

The third day was about evaluating the possible solutions for scheduling tasks in Kotlin/Java. I ended up using the built in solution because it is easy to use and we don't need millisecond precision at timing for this type of task.

The last three days were about starting to implement the features while trying to comply with the concepts of TDD.


### My solution

After having a look at the behaviour of the PaymentProvider I realised that the two statuses (PENDING, PAID) will not be enough for an Invoice. The Payment Provider sometimes won't succeed and those cases should be handled. Apart from the happy case, there is also the case when the Customer has insufficient funds or when a NetworkError occurs. These two can happen with any customer so in this case an automatic retry mechanism can be enough. On the other hand, there are behaviours like CurrencyMismath or CustomerNotFound, when an automatic solution cannot do anything. In this case human interaction should be in order.

I ended up with the following InvoiceStatuses:
* PENDING - inidcates that the invoice shall be paid
* PAID - indicates that the invoice is paid
* FAILED1 - indicates that for the first time we failed to pay the invoice and it should be tried again
* FAILED2 - indicates that for the second time we failed to pay the invoice and it should be tried again
* FAILED3 - indicates that for the third time we failed to pay the invoice and it should be tried again
* MANUAL_CHECK - indicates that the invoice shall be handled manually (due to errors or after 4 failed payment attempts)

This decision somewhat requires a change on the interface of the PaymentProvider. However, I figured that the Payment Provider should not care about the status of an Invoice. Maybe in the DTO the status can be omitted.

At the beginning of each billing cycle (month) 4 payment attempts will occur:
1. PENDING: At the first day of the month the regular PENDING bills will be handled
2. FAILED1: The ones that have failed in the previous attempt will be attempted again (2nd day)
3. FAILED2: The ones that have failed in the previous attempt will be attempted again (3rd day)
4. FAILED3: The ones that have failed in the previous attempt will be attempted again (4th day)

For testing purposes the period times between two cycles and between two attempts can be configured.

Due to the design of the scheduling library the scheduled jobs run on a different thread which is sufficient for this task.

### Improvement ideas

I have two main concerns regarding this solution:

#### Scalability
With this solution at the beginning of each month only 4 jobs will be run. However if there are a lot of records (millions) in the database these jobs can run for a long time. If this is the case using a distributed database can be a solution and each partition can be handled by different instances of this application.

It's also possible that since payment takes more time, the payment provider should handle the distribution.

However putting the problem into context somewhat mitigates this issue. The customers here are companies and not people. Obviously the amount of companies is far less than the amount of humans. So maybe we shouldn't plan for millions of records just yet. :)

#### Single responsibility principle

To my mind this task should be handled by a sole microservice, which gathers the list of invoices and customers from another service. It would be problematic if at the beginning of each month the whole system slows down. This way the problem can be prevented.

#### Improve code quality

The quality of the code could be better. Especially the tests. I hope that by improving my skill in Kotlin, I can create better quality code.

The testing could be more exhaustive. Only the main cases of the solution are tested.



## Antaeus

Antaeus (/ænˈtiːəs/), in Greek mythology, a giant of Libya, the son of the sea god Poseidon and the Earth goddess Gaia. He compelled all strangers who were passing through the country to wrestle with him. Whenever Antaeus touched the Earth (his mother), his strength was renewed, so that even if thrown to the ground, he was invincible. Heracles, in combat with him, discovered the source of his strength and, lifting him up from Earth, crushed him to death.

Welcome to our challenge.

## The challenge

As most "Software as a Service" (SaaS) companies, Pleo needs to charge a subscription fee every month. Our database contains a few invoices for the different markets in which we operate. Your task is to build the logic that will pay those invoices on the first of the month. While this may seem simple, there is space for some decisions to be taken and you will be expected to justify them.

### Structure
The code given is structured as follows. Feel free however to modify the structure to fit your needs.
```
├── pleo-antaeus-app
|
|       Packages containing the main() application. 
|       This is where all the dependencies are instantiated.
|
├── pleo-antaeus-core
|
|       This is where you will introduce most of your new code.
|       Pay attention to the PaymentProvider and BillingService class.
|
├── pleo-antaeus-data
|
|       Module interfacing with the database. Contains the models, mappings and access layer.
|
├── pleo-antaeus-models
|
|       Definition of models used throughout the application.
|
├── pleo-antaeus-rest
|
|        Entry point for REST API. This is where the routes are defined.
└──
```

## Instructions
Fork this repo with your solution. We want to see your progression through commits (don’t commit the entire solution in 1 step) and don't forget to create a README.md to explain your thought process.

Please let us know how long the challenge takes you. We're not looking for how speedy or lengthy you are. It's just really to give us a clearer idea of what you've produced in the time you decided to take. Feel free to go as big or as small as you want.

Happy hacking 😁!

## How to run
```
./docker-start.sh
```

## Libraries currently in use
* [Exposed](https://github.com/JetBrains/Exposed) - DSL for type-safe SQL
* [Javalin](https://javalin.io/) - Simple web framework (for REST)
* [kotlin-logging](https://github.com/MicroUtils/kotlin-logging) - Simple logging framework for Kotlin
* [JUnit 5](https://junit.org/junit5/) - Testing framework
* [Mockk](https://mockk.io/) - Mocking library
