# Automagic

[![Build Status](https://travis-ci.org/cb372/automagic.png?branch=master)](https://travis-ci.org/cb372/automagic)

## What's this all about?

When you want to pass data across a boundary between two [bounded contexts](http://martinfowler.com/bliki/BoundedContext.html), you often have to make a transformation between two very similar models. 

For example, you might be passing data from one microservice to another. You don't want to share the same model across both services, because they contain subtle differences and serve different purposes. On the other hand the transformation between them can take a lot of time and effort.

To take an example based on the actual use case that prompted the creation of Automagic, imagine you have a Content Management System (CMS) for creating news content and a backend service that exposes that content on an API for consumption by frontend clients. Whenever somebody creates or updates a news article, the CMS service sends that data to the backend service via a queue.

The backend service takes articles off the queue, transforms them from the CMS's model to its own, and writes them to its datastore.

The models have a lot of fields and are heavily nested, so writing the transformations by hand can be a soul-destroying process:

```scala
def transformArticle(input: CMSArticle) = Article(
    id = input.id,
    headline = input.headline,
    body = input.body,
    trailText = input.trailText,
    thumbnailUrl = input.thumbnailUrl,
    legallySensitive = input.legallySensitive,
    createdAt = new DateTime(input.createdAt),
    createdBy = transformUser(input.createdBy),
    tags = input.tags.map(transformTag),
    // ... and so on, ad nauseum ...
)

def transformUser(input: CMSUser) = User(
    id = input.id,
    firstName = input.firstName,
    lastName = input.lastName,
    email = input.email
)

def transformTag(input: CMSTag) = Tag(
    id = input.id,
    name = input.name,
    description = input.description,
)
```

This is where Automagic comes in. It uses a macro to automagically convert from one model to the other. Behold!

```scala
import automagic._

def transformArticle(input: CMSArticle): Article = transform[CMSArticle, Article](input,
    "createdAt" -> new DateTime(input.createdAt),
    "createdBy" -> transform[CMSUser, User](input.createdBy),
    "tags" -> input.tags.map(tag => transform[CMSTag, Tag](tag))
```

This achieves the same result as the hand-written code, without putting you through boilerplate Hell.

Of course, it doesn't have to be for transforming models between microservices. Converting between DAOs and domain models inside an application would be another use case for Automagic.

## How does it work?

The core of Automagic is a Scala macro that constructs an instance of the required type, copying the values of the input object's fields to the newly created object.

In the simplest case (e.g. the transformation of users and tags in the example above), it simply takes the values of the input object's fields and passes them the output object's constructor.

Sometimes things are a little more complicated than that: you might want to add extra fields that aren't provided by the input, or replace the input fields with values that you supply. To deal with this, Automagic lets you supply overrides.

In the transformation of articles in the example above, three of the `Article` class's constructor parameters (`createdAt`, `createdBy` and `tags`) were supplied via overrides. The rest of the parameters were copied from the fields of the input object.

### Instance construction

To construct an instance, Automagic first tries all `apply` methods on the companion object. Then it tries the class's primary constructor.

As soon as it finds a constructor it can use (i.e. it successfully fills in all the constructor arguments using the input fields and overrides), it generates code that invokes that constructor.

If it can't use any of the constructors, it gives up and gives you a compile error with details of what it tried.

Automagic can be used to construct case classes, normal classes, and classes built by an `apply` method in the companion object.

Note that the order of the parameters does not matter. Automagic only identifies parameters using their names and does not care about order.

## Type safety

All the types of the input fields and overrides are checked at compile time. If something is amiss, you'll get a nice compile error telling you what went wrong:

```
[error] /Users/cbirchall/code/myapp/ModelTransformation.scala:177: Failed to find any suitable constructors for class Event. Tried the following:
[error]
[error] Event.apply(title: Option[String], venue: Option[String], location: Option[String], price: Option[String], start: Option[org.joda.time.DateTime], end: Option[org.joda.time.DateTime])
[error]   ↳ Cannot find a suitable value for parameter 'end'
[error]
[error] new Event(title: Option[String], venue: Option[String], location: Option[String], price: Option[String], start: Option[org.joda.time.DateTime], end: Option[org.joda.time.DateTime])
[error]   ↳ Cannot find a suitable value for parameter 'end'
[error]
[error]
[error]   private def transformEvent(event: CMSEvent): Event = transform[CMSEvent, Event](event,
[error]                                                                                  ^
[error] one error found
[error] (compile:compileIncremental) Compilation failed
```

## How to use

In your sbt file:

```
libraryDependencies += "com.github.cb372" %% "automagic" % "0.2.1"
```

In your code:

```scala
import automagic._

val theirModel: TheirModel = ...

val myModel: MyModel = transform[TheirModel, MyModel](theirModel)

// or, if you need to supply some overrides
val myModel: MyModel = transform[TheirModel, MyModel](theirModel, "foo" -> 123, "bar" -> "baz")
```

## Requirements

Works with Scala 2.11 or newer.

## Acknowledgements

[This blog post](http://www.strongtyped.io/blog/2014/05/23/case-class-related-macros/) by Luc Duponcheel was really helpful.

I also found plenty of useful information in StackOverflow answers written by the usual suspects, chiefly Travis Brown (@travisbrown).
