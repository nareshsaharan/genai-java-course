# Java Fundamentals

## What is Java

Java is a statically-typed, object-oriented programming language designed to
run on any device via the Java Virtual Machine (JVM) — "write once, run
anywhere." Source code (.java files) compiles to platform-independent
bytecode (.class files), which the JVM interprets or JIT-compiles into
native machine code at runtime. Java is garbage-collected, meaning the
runtime automatically reclaims memory for objects that are no longer
reachable, removing the need for manual memory management like in C/C++.

## Primitive Types vs Reference Types

Java has eight primitive types: byte, short, int, long, float, double,
char, and boolean. Primitives store their actual value directly and live on
the stack when used as local variables. Everything else — classes, arrays,
interfaces — is a reference type: the variable holds a reference (pointer)
to an object stored on the heap. Autoboxing automatically converts between
a primitive and its wrapper class (e.g. int to Integer) when needed, such as
when adding a primitive to a generic collection like List<Integer>.

## Collections Framework

The Java Collections Framework provides a unified architecture for storing
and manipulating groups of objects. The core interfaces are List (ordered,
allows duplicates — ArrayList, LinkedList), Set (no duplicates — HashSet,
TreeSet, LinkedHashSet), Map (key-value pairs — HashMap, TreeMap,
LinkedHashMap), and Queue/Deque (FIFO/double-ended access). ArrayList is
backed by a resizable array and gives O(1) indexed access but O(n) insertion
in the middle; LinkedList gives O(1) insertion/removal at the ends but O(n)
indexed access. HashMap offers average O(1) get/put by hashing keys into
buckets, but makes no guarantee about iteration order — use LinkedHashMap if
insertion order matters, or TreeMap if sorted-key order matters.

## Exception Handling

Java exceptions are objects that represent an abnormal condition. Checked
exceptions (subclasses of Exception, excluding RuntimeException) must be
either caught or declared in a method's `throws` clause — the compiler
enforces this, used for recoverable conditions like IOException. Unchecked
exceptions (subclasses of RuntimeException, like NullPointerException or
IllegalArgumentException) represent programming errors and aren't required
to be declared. try-with-resources automatically closes any resource
implementing AutoCloseable at the end of the block, even if an exception is
thrown, replacing the older try/finally boilerplate for closing streams,
connections, and files.

## Streams and Lambdas

Introduced in Java 8, the Stream API lets you process collections
declaratively: `list.stream().filter(x -> x > 10).map(x -> x * 2)
.collect(Collectors.toList())` filters, transforms, and collects without
manual loops. Streams are lazy — intermediate operations (filter, map,
sorted) don't execute until a terminal operation (collect, forEach, reduce,
count) is invoked. Lambda expressions (`x -> x * 2`) are anonymous functions
that implement a functional interface (an interface with exactly one
abstract method, like `Function<T,R>` or `Predicate<T>`), enabling
functional-style programming without verbose anonymous inner classes.

---

# Object-Oriented Programming (OOP) Principles

## Encapsulation

Encapsulation bundles data (fields) and the methods that operate on that
data into a single unit (a class), while restricting direct external access
to the internal state. In Java this is done with access modifiers — fields
are typically declared `private`, and controlled access is provided through
public getter/setter methods. This lets a class change its internal
representation later without breaking code that depends on it, and lets the
class enforce invariants (e.g. a setter can reject a negative age) that
direct field access could never guarantee.

## Inheritance

Inheritance lets a class (the subclass) acquire the fields and methods of
another class (the superclass) using the `extends` keyword, modeling an
"is-a" relationship (a Dog is an Animal). The subclass can override
superclass methods to provide specialized behavior, and can add new fields
and methods of its own. Java supports only single inheritance of classes
(a class can extend exactly one superclass) to avoid the diamond problem,
but a class can implement multiple interfaces, which is how Java achieves
a safe form of multiple inheritance of type.

## Polymorphism

Polymorphism means "many forms" — the same method call can behave
differently depending on the actual runtime type of the object it's called
on. Java achieves this through method overriding (runtime/dynamic
polymorphism: a subclass provides its own implementation of a superclass
method, and the JVM picks which version to run based on the object's actual
class at runtime, not the reference's declared type) and method overloading
(compile-time/static polymorphism: multiple methods share a name but differ
in parameter types or count, and the compiler picks the matching one at
compile time based on the arguments). Polymorphism is what lets you write
`Animal a = new Dog(); a.makeSound();` and have it correctly call Dog's
`makeSound()` even though `a` is declared as type Animal.

## Abstraction

Abstraction means exposing only the essential behavior of an object while
hiding the implementation details behind it. Java provides two mechanisms:
abstract classes (declared with `abstract`, can contain both abstract
methods with no body and concrete methods with implementation, and cannot
be instantiated directly) and interfaces (traditionally 100% abstract
method signatures with no implementation, though since Java 8 interfaces
can also have `default` and `static` methods with bodies). A caller working
against an interface type (e.g. `List<String> list = new ArrayList<>();`)
doesn't need to know or care which concrete implementation is behind it.

## SOLID Principles

SOLID is a set of five object-oriented design principles that make software
more maintainable and extensible: **S**ingle Responsibility (a class should
have only one reason to change — one job), **O**pen/Closed (classes should
be open for extension but closed for modification — add new behavior via
new subclasses/implementations rather than editing existing tested code),
**L**iskov Substitution (a subclass object must be usable anywhere its
superclass is expected, without breaking correctness), **I**nterface
Segregation (many small, focused interfaces are better than one large
general-purpose interface that forces implementers to support methods they
don't need), and **D**ependency Inversion (high-level modules should depend
on abstractions, not concrete low-level implementations — this is exactly
what Spring's dependency injection enables at the framework level).

---

# Spring Boot

## What is Spring Boot

Spring Boot is an opinionated extension of the Spring Framework that
eliminates most of the manual configuration Spring traditionally required.
It provides auto-configuration (sensible defaults inferred from the
libraries on your classpath — e.g. adding `spring-boot-starter-web` auto-
configures an embedded Tomcat server and Spring MVC), starter dependencies
(curated dependency bundles like `spring-boot-starter-data-jpa` that pull in
everything needed for a feature with one line in your build file), and an
embedded servlet container, so a Spring application can run as a standalone
executable JAR with `java -jar app.jar` instead of needing a separately
deployed application server.

## Inversion of Control and Dependency Injection

Inversion of Control (IoC) is the principle that an object should not
construct or look up its own dependencies — instead, something external
(the Spring container) creates and provides them. Dependency Injection (DI)
is how Spring implements IoC: dependencies are "injected" into a class,
typically via constructor parameters (the recommended approach, since it
makes dependencies explicit and immutable, and works well for testing since
you can construct the class directly with mocks/fakes without needing
Spring at all). Spring manages beans (objects it constructs and wires
together) in an ApplicationContext, resolving each bean's dependencies by
type (and by name/qualifier when multiple candidates exist).

## Core Annotations

`@SpringBootApplication` is a convenience annotation combining
`@Configuration` (marks a class as a source of bean definitions),
`@EnableAutoConfiguration` (turns on Spring Boot's auto-configuration), and
`@ComponentScan` (tells Spring to scan the package and sub-packages for
components). `@Component` (and its specializations `@Service` for business
logic, `@Repository` for data-access classes — which also translates
persistence exceptions into Spring's unified exception hierarchy — and
`@Controller`/`@RestController` for web endpoints) mark a class to be
auto-detected and registered as a Spring bean. `@Autowired` requests
dependency injection, though modern Spring style prefers a single
constructor with no `@Autowired` needed at all, since Spring implicitly
autowires the sole constructor.

## Building REST APIs

`@RestController` combines `@Controller` and `@ResponseBody`, meaning every
method's return value is serialized directly into the HTTP response body
(typically as JSON, via Jackson) rather than being resolved as a view name.
`@GetMapping`, `@PostMapping`, `@PutMapping`, and `@DeleteMapping` are
shorthand for `@RequestMapping` with a fixed HTTP method, mapping a method
to a URL path. `@RequestBody` binds an incoming JSON request body to a Java
object; `@PathVariable` extracts a value from the URL path (e.g. the `{id}`
in `/api/users/{id}`); `@RequestParam` extracts a query-string parameter.
`@Valid` combined with Bean Validation annotations (`@NotBlank`,
`@Positive`, `@Size`, etc.) on a request DTO triggers automatic validation
before the controller method body runs, returning a 400 response with
field-level errors if validation fails.

## Spring Data JPA

Spring Data JPA removes most of the boilerplate of writing data-access code
by letting you declare a repository interface (e.g. `interface UserRepository
extends JpaRepository<User, Long>`) and get a full set of CRUD operations
for free, with no implementation required — Spring generates a proxy
implementation at runtime. Query methods can often be derived purely from
their name (e.g. `findByEmailAndActiveTrue(String email)` generates the
matching SQL automatically), and `@Query` lets you write JPQL or native SQL
directly when a derived query name would be too unwieldy. `@Transactional`
marks a method (or class) as needing to run inside a database transaction,
so all its database operations either fully commit together or fully roll
back together if an exception propagates out of the method.

## Testing in Spring Boot

`@SpringBootTest` boots a full (or sliced) Spring ApplicationContext for
integration-style tests. Test slices load only the relevant part of the
context for faster, more focused tests: `@WebMvcTest` loads just the web
layer (controllers, filters, JSON serialization) with the ability to mock
service-layer dependencies via `@MockitoBean`, while `@DataJpaTest` loads
just the JPA/repository layer against an embedded or Testcontainers-backed
database. Plain unit tests that don't need any Spring context at all
(constructing a service class directly with `new`, passing mocked
dependencies via its constructor) are the fastest and should be preferred
whenever a class's logic doesn't actually depend on Spring wiring itself.

---

# Generative AI (GenAI) Fundamentals

## What is Generative AI

Generative AI refers to machine learning models that create new content —
text, images, audio, code — rather than just classifying or predicting a
label for existing input. Large Language Models (LLMs) like Claude, GPT,
Gemini, and Llama are a category of generative AI trained on massive text
corpora to predict the most probable next token given the preceding
context, one token at a time. Despite this simple training objective,
sufficiently large models develop surprisingly broad capabilities:
answering questions, writing code, summarizing documents, and reasoning
through multi-step problems, all from the same underlying next-token
prediction mechanism.

## Tokens, Context Windows, and Prompting

LLMs don't process raw text directly — they first split it into tokens
(roughly word-fragments; "unhappiness" might become "un", "happi", "ness").
A model's context window is the maximum number of tokens (input plus
generated output combined) it can attend to in a single request; exceeding
it means older content gets truncated or the request is rejected outright.
Prompt engineering is the practice of structuring the input text to reliably
get the desired output — providing clear instructions, examples
(few-shot prompting), explicit output-format requirements, and separating
system-level instructions (persistent behavior rules) from user-level
messages (the specific request).

## Retrieval-Augmented Generation (RAG)

RAG grounds an LLM's answers in a specific set of documents rather than
relying solely on what the model memorized during training. The pipeline
works in four steps: (1) ingest — split source documents into chunks and
convert each chunk into a numeric vector (an embedding) that captures its
semantic meaning; (2) store — save those vectors in a vector database
alongside the original text; (3) retrieve — when a question comes in,
embed the question the same way, then find the stored chunks whose vectors
are most similar (typically via cosine similarity) to the question's
vector; (4) generate — pass only those retrieved chunks plus the question
to the LLM, instructing it to answer using only that provided context. RAG
reduces hallucination (the model inventing plausible-sounding but false
information) and lets an application answer questions about private,
proprietary, or very recent content the model was never trained on.

## Embeddings and Vector Databases

An embedding is a fixed-length array of floating-point numbers (e.g. 384,
768, or 1536 dimensions) produced by an embedding model, positioned in a
high-dimensional space such that semantically similar text ends up with
vectors that are close together (measured by cosine similarity or Euclidean
distance) and dissimilar text ends up far apart. A vector database (like
pgvector, an extension that adds a vector column type and similarity search
operators directly to PostgreSQL, or dedicated systems like Pinecone and
Qdrant) is optimized for the specific query pattern RAG needs: "given this
query vector, find the K nearest stored vectors" efficiently, even across
millions of stored chunks, typically using approximate nearest-neighbor
indexes like HNSW (Hierarchical Navigable Small World graphs) to avoid
comparing the query against every single stored vector.

## LangChain4j

LangChain4j is a Java library that provides a consistent abstraction layer
over many different LLM and embedding providers (Anthropic, OpenAI, Google
Gemini, and others), so application code can depend on a stable interface
(`ChatModel`, `EmbeddingModel`) rather than any single provider's specific
SDK and request format. Its `AiServices` feature lets you declare a plain
Java interface with annotated methods (`@SystemMessage`, `@UserMessage`)
and get back a working implementation backed by an LLM at runtime,
including automatic parsing of the model's text response into a typed Java
object (record or POJO) when the interface method's return type isn't a
plain String. This is the same mechanism that lets a RAG application swap
its underlying chat provider (e.g. from Claude to a free-tier alternative
like Groq or Gemini) without changing any of the calling code.

## Hallucination and Grounding

Hallucination is when an LLM generates text that is fluent and confident
but factually wrong or entirely fabricated — a direct consequence of the
model generating the statistically most plausible next token rather than
consulting a verified source of truth. Grounding is the general strategy
for reducing hallucination: constraining the model's answer to only what's
present in explicitly supplied context (as RAG does), rather than letting
it draw freely on its training-time "memory." A well-grounded system should
also be designed to recognize when the supplied context doesn't actually
contain an answer, and respond with an honest "I don't have enough
information" rather than guessing — silently falling back to the model's
general knowledge would defeat the entire purpose of grounding.
