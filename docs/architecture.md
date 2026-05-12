# Architecture

The technical companion to [`overview.md`](overview.md). Read the
overview first if you haven't — that document explains *what* the
project does in plain terms. This one explains *how* it's built,
why we chose that structure, and the strongest arguments against
the choice.

The document is long on purpose. It is structured so you can read it
in order from top to bottom, but you can also dip in: each section
stands on its own.

---

## 1. What is in the codebase

The repository is a single sbt build with three subprojects:

| Subproject | Role                                                                                      |
|------------|-------------------------------------------------------------------------------------------|
| `sut`      | The **system under test** — small example methods. Compiled with source-level coverage instrumentation. |
| `engine`   | The **framework** — the hexagonal-architecture core (domain types, ports, adapters, use case). |
| `runner`   | The **driver** — an `IOApp` that points the engine at SUT methods. Runs the JVM under a runtime coverage agent. |

Almost all of the interesting code lives in `engine/`. The runner is
ten lines of wiring; the SUT is a dozen lines of example methods.

The driven ports and their adapters today:

| Port (what the use case needs)    | Adapter                                       | What this adapter is                                  |
|-----------------------------------|-----------------------------------------------|-------------------------------------------------------|
| `InputGenerator`                  | `RandomInputGenerator`                        | Uniform `Int` from ScalaCheck                         |
| `InputGenerator`                  | `GuidedInputGenerator`                        | Placeholder for the guided strategy — prints feedback |
| `BranchCoverageTracker`           | `JacocoBranchCoverageTracker`                 | Per-input bytecode probes via the JaCoCo agent        |
| `BranchTreeBuilder`               | `ScalametaBranchTreeBuilder`                  | Static AST shape + source positions, via Scalameta    |
| `SourceCoverageReader`            | `ScoverageSourceCoverageReader`               | Per-AST-statement invocation, via scoverage           |
| `CoverageReportWriter`            | `FileSystemCoverageReportWriter`              | DOT · SVG · CSV · JSON · TXT to disk                  |

The single driving port `TestRunner` is implemented by the use case
`TestRunnerHandler`; it is composed and exposed through the driving
adapter `FileSystemTestRunner`.

---

## 2. The architectural question

The thesis is a comparison: random property-based testing versus
coverage-guided property-based testing. To do that comparison
honestly, both flavours have to run inside the *same* test harness,
producing the *same* outputs, looking at the *same* methods, with
the *same* measurement machinery in the middle. The only thing
allowed to differ is the part that chooses the next input.

Imagine writing this as a flat script: a single file that opens a
source file, parses it, starts a coverage agent, loops one hundred
times calling some random generator, writes a chart at the end.
That works for one strategy. But the moment you want to swap the
generator from random to guided, you discover that the random
choice is glued into a dozen places in the file — the seed, the
generator type, the place where the loop calls it, the assumption
that the input is unrelated to past coverage. So you copy the file,
edit those dozen places, and now you have two flat scripts that
share 95 % of their text. When you find a bug in one, you have to
fix it in the other. When you want to add a third strategy you copy
again. It rots quickly.

The architectural question is: *how do we organise the framework so
that swapping a single decision — "how do we pick the next input?"
— is a single, contained change?*

The answer this project gives is **ports and adapters**, also
called hexagonal architecture.

---

## 3. The pattern, in the simplest terms

A wall outlet doesn't know what you're going to plug into it. It
doesn't care whether you're connecting a toaster, a lamp, a vacuum,
or an electric guitar. It only promises one thing: at this shape of
hole, you will get electricity in a known voltage and frequency.
The "shape of the hole" is what makes it possible for appliances
and outlets to be designed independently.

That shape — the contract — is what the pattern calls a **port**.
The toaster, the lamp, the vacuum: those are **adapters**. Each
adapter implements the port in its own way, but to the building's
electrical system they all look exactly the same.

In software, a port is a piece of code that says, in effect, "I need
something that can do X, but I don't care how." An adapter is a
concrete thing that fulfils that promise.

The discipline is twofold:

- The **core logic** of the application is written entirely in terms
  of ports. It does not import any concrete adapter. It does not
  know that the coverage data comes from JaCoCo, or that the report
  is written to a file. It only knows: "there's a coverage tracker;
  I can ask it for a measurement."
- The **adapters** are written so they fit a port without leaking
  any of their concrete-ness into it. The JaCoCo adapter does not
  put a method like `getJacocoExecutionData` on the port. It puts
  `measure`. Generic word, generic shape.

If you follow that discipline, you end up with a system that has a
small, well-defined core in the middle (the "hexagon"), surrounded
by a thin layer of adapters that plug it into the real world. Swap
the adapter, leave the core alone; swap the core, leave the
adapters alone.

---

## 4. How the pattern lands here

The diagram below is the abstract view: it shows the pattern, not
the specific tooling. Every concrete name (JaCoCo, scoverage,
Scalameta) lives in §1's table, not in the figures.

![Hexagonal architecture](images/hexagon.png)

There are two "sides" to the hexagon:

- The **driving side** (left) is who calls *into* the framework. In
  our project this is the application's entry point — the small
  program that says "test these three methods." It talks to the
  framework through the `TestRunner` driving port.

- The **driven side** (right) is what the framework calls *out to*.
  In our project, five small contracts: `InputGenerator`,
  `BranchCoverageTracker`, `BranchTreeBuilder`,
  `SourceCoverageReader`, `CoverageReportWriter`.

The use case (`TestRunnerHandler`) is written entirely in terms of
these ports. The driving adapter (`FileSystemTestRunner`) is the
*one* file in the codebase that imports concrete adapter classes
and wires them together.

---

## 5. The five driven ports, one by one

It's worth pausing on each port and asking: *why is this a separate
contract? Why not just bake it into the use case?*

### 5a. `InputGenerator`

**What it does.** Produces the next integer input on demand. Each
time the loop wants a fresh input, it calls the generator with the
session's current state (`SessionFeedback`).

**Why a port.** This is the very thing the thesis compares between
strategies. Random vs guided is literally a difference of which
input-generator adapter is plugged in. If this lived in the use
case, the comparison would be a fork in the code; as a port, it's a
single line change in the wiring.

**What it lets us swap.** Today, random and (placeholder) guided.
Tomorrow, mutation-based, dictionary-based, gradient-based — anything
that fits the same shape.

### 5b. `BranchCoverageTracker`

**What it does.** Resets coverage between sessions, and returns a
per-line "what was covered just now and what's been covered so far"
snapshot after each input.

**Why a port.** Bytecode-level coverage is a domain unto itself.
JaCoCo is the obvious choice for Scala because of its agent model,
but there are alternatives (JVMTI agents, custom ASM
instrumentation, sampling profilers). Hiding that choice behind a
port keeps the option open without polluting the use case with
JaCoCo-specific types.

**What it lets us swap.** A different runtime coverage source would
be a different adapter. Even if we never swap it, the use case
becomes easy to test in isolation — see §7.

### 5c. `BranchTreeBuilder`

**What it does.** Takes a Scala source file and a method name, and
returns the method's enclosing package, class, and a tree of its
branchy expressions (`if`, `match`, `while`, …). Each node carries
its source position.

**Why a port.** Parsing Scala is a heavy lift; we use Scalameta. But
the *purpose* of this port is not "parse Scala" — it's "describe
the structure of a method". A toy alternative might be a
hand-rolled parser that only handles `if`/`else`; a future
alternative might be one based on the Scala 3 compiler. The port
stays the same.

**What it lets us swap.** Other parsers, or even pre-cached parse
results from disk, without touching the use case.

### 5d. `SourceCoverageReader`

**What it does.** At the end of a session, returns the set of source
positions (character offsets) that were exercised during the run.

**Why a port.** A deliberately weaker promise than the
`BranchCoverageTracker` port. This one doesn't operate per-input; it
speaks only in cumulative source positions. That makes it easy to
support multiple back-ends — scoverage today, possibly llvm-cov for
native code tomorrow.

**What it lets us swap.** The whole story of how source-level
coverage is collected. Today it's a compiler plugin instrumenting
statements; tomorrow it might be sampling a debug-info table at
runtime.

### 5e. `CoverageReportWriter`

**What it does.** Takes a finished `SessionReport` and writes it
somewhere.

**Why a port.** Output is the most likely surface to change. Today
we write five files to disk. Tomorrow we might want to push the
report to a database for cross-session comparisons; or to an HTML
dashboard; or to Prometheus for monitoring. Each of those is one
new adapter and zero changes to the use case.

**What it lets us swap.** Output format and destination.

---

## 6. The system in motion

Two diagrams help here.

### 6a. Session choreography

The shape of one `TestRunner` call from the application down to the
written report:

![Session choreography](images/sequence.png)

The yellow band is the inner loop: ask the generator for an input,
run the property, read a coverage delta, repeat. Only *after* the
loop closes does the use case fetch the static branch tree and the
per-position coverage snapshot for the writer. That post-loop
fetch is what gives the writer enough information to colour every
AST node accurately — it's the bit of work that turns "coverage
percentages" into "this exact branch was reached / not reached".

### 6b. Per-iteration feedback cycle

Zooming into the loop body alone:

![Per-iteration feedback cycle](images/loop.png)

`Session Feedback` is both the loop's running accumulator and the
read-only view handed to the generator on the *next* iteration. One
shape serves both roles, which is why there is no separate "loop
state" type in the code. This is the surface through which a future
guided strategy will steer the inputs.

---

## 7. The data the framework produces

Schematically, a `SessionReport` looks like:

```
Session report
├── method, source file, total inputs
├── method tree                ── from static AST analysis
├── per-line branch summaries  ── counter + hit count + first hit
├── input log                  ── (index, input, lines exercised)
├── growth curve               ── cumulative covered branches over time
├── saturation input index
└── covered source positions   ── from statement coverage
```

The loop-internal `SessionFeedback` carries the same data *minus*
the post-session snapshot — it's the read-only summary handed to
the generator. Same fields, different name, different audience:
the writer sees the full report; the generator sees the running
feedback.

---

## 8. What we got out of the discipline

Each item below is something the project actually uses or will use
— none of it is speculative.

**Swapping the strategy is one line.** Switching `Strategy.Random`
to `Strategy.Guided` is a single change at the call-site in
`Main.scala`. The wiring in `FileSystemTestRunner` already routes
the choice through the appropriate generator. No other file
changes. This is exactly the property the thesis comparison needs.

**The use case is testable in isolation.** Each port is an
interface. Feed the handler fake implementations that return
scripted data, and the whole orchestration logic can be checked
without launching a JaCoCo agent or touching the file system. This
isn't theoretical — anybody picking up the codebase gets it for
free.

**The boundaries are honest.** When you read
`TestRunnerHandler.scala`, you can tell at a glance everything the
use case touches in the outside world: it's the list of constructor
parameters. There are exactly five things the handler can do that
have side effects. Anything else is in-memory computation.

**Two implementations of one port coexist peacefully.** Random and
Guided share the `InputGenerator` port. Both are in the codebase
right now. Both are tested by the same handler. The choice between
them is a one-word change. This is the central justification for
the whole pattern in this project.

**Future extensions are clear, not speculative.** When somebody
says "can we add an HTML report?", the answer is "write a new
adapter for the report-writer port; nothing else has to change."
When somebody says "can we test on Java code too?", the answer is
"write a new tree-builder adapter for Java." The cost of adding
things is bounded by the number of new adapters.

**Adapters are independent.** When scoverage broke between versions
(specifically, when its `Statement` class moved `methodName` to a
nested `Location` object), the fix was confined to *one file* and
*one line*. Nothing else had a dependency on scoverage's internal
shape, so nothing else needed to change.

---

## 9. The honest costs

A thesis reader could push back on the architecture in several
defensible ways. Let's list them.

**"You have twenty-five Scala files for a hundred-line idea."** True.
A flat script could express the same behaviour in five or six files,
maybe fewer.

*Response.* For a one-shot script, this would be wasteful. For a
thesis whose entire contribution is *a comparison of strategies*,
file count is the wrong metric. The right metric is the size of
the swap when you change a strategy. That swap is one line. Worth
twenty extra files.

**"Most ports have only one adapter. The abstraction never pays off."**
Also true. Four of the five driven ports have exactly one adapter
today. The `BranchTreeBuilder` isn't likely ever to have a second
implementation in this project's lifetime. So why the port?

*Response.* Three reasons. First, the cost of keeping a port for a
one-adapter case is roughly the trait definition — a dozen lines.
The use case would have to depend on *something*; depending on a
trait isn't meaningfully heavier than depending on a class. Second,
the *uniformity* matters: once the convention is "the use case
depends only on ports", you can read any file in the codebase
without wondering whether a particular call is leaking
implementation detail. Third, the one port where multiple adapters
exist is the most important one of the whole project. Drawing the
line at "the ports that need multiple adapters" creates a special
case at the heart of the architecture; drawing it at "everything is
a port" eliminates the special case.

**"The pattern adds indirection that makes the code harder to
follow."** Partial truth. A new reader has to follow calls through
interface boundaries, which is more work than reading a flat
function top to bottom. Especially true when debugging.

*Response.* The cure is good naming (each port's name tells you
what to expect when you click through) and small, locatable
adapters. We name them by the technology that backs them
(`JacocoBranchCoverageTracker`, `ScalametaBranchTreeBuilder`), so
navigating from the call to the impl is a single grep. The bigger
conceptual hurdle in the codebase isn't *which file to read next* —
it's *what coverage actually is*.

**"You're using the pattern because it sounds smart, not because
you need it."** This is the strongest version of the criticism.

*Response.* If we only had a random strategy and were never going
to write a guided one, this critique would be devastating. The
pattern would be cargo-culted and a flat script would be the
honest choice. The thesis's central question — *does guided beat
random?* — is what justifies the architecture. Without the
comparison, the architecture is overbuilt. With it, the
architecture is exactly the right size.

**"What about the build complexity? Multiple subprojects, sbt
configuration, JaCoCo agent wiring …"** True. The project's build
file is more involved than for a flat script. But almost none of
that build complexity is *caused* by the ports-and-adapters layer.
It's caused by:

- needing to compile the SUT with scoverage instrumentation
  (required by any approach),
- needing to run the SUT under a JaCoCo agent (likewise),
- the desire to isolate the SUT, the engine, and the runner from
  each other so they can evolve independently (a build-level
  concern, not an architectural one).

The pattern adds nothing to `build.sbt`. It only adds files inside
`engine/src/main/scala/`.

---

## 10. Alternatives we did not pick

To make the choice meaningful you have to know what else was on
the table.

### 10a. Plain flat script

Everything in one file. Open the source, start the agent, loop,
write the chart, done.

This is the obvious choice for a 200-line experiment. It would
have been faster to write and easier for a reader to follow on a
single screen. It fails as soon as we add the guided strategy
because the random choice is woven through too many parts of the
file to be cleanly split. You'd end up either (a) copying the file
to make a guided version and accepting permanent drift, or (b)
introducing some kind of "strategy" type half-heartedly — which is
exactly the first step toward ports and adapters anyway. Better to
commit to the discipline early.

### 10b. Layered architecture (the classic "three-tier")

You stack the code in three layers: data access at the bottom,
business logic in the middle, presentation at the top. Each layer
calls the one below.

It would have been workable, but layered architecture implicitly
assumes a *direction* of dependency: lower layers don't know about
higher ones, but they're still concrete; the business layer
depends on the data layer being database-shaped. For us, the
business logic doesn't have one clean "below" — it talks to a
coverage agent, a parser, a file system, a generator. Putting all
of those in a single "infrastructure" layer is either too coarse
(four very different concerns lumped together) or too fine
(sub-layered, and you've reinvented ports and adapters with extra
steps).

### 10c. Clean / onion architecture

Robert Martin's variant: concentric rings of "entities", "use
cases", "interface adapters", "frameworks". Conceptually a sibling
of ports and adapters.

Could have worked. The semantic difference for a project of this
size is hair-splitting: both patterns end up with a use-case object
depending only on interfaces, those interfaces being implemented by
adapter classes. Clean architecture's extra ring (entities separate
from use cases) doesn't pay off here because our domain types are
already small data classes — there is no behaviour-rich "entity"
layer to separate. We'd be drawing a ring around nothing.

We picked ports and adapters because it's the leaner formulation
for the same idea. We could swap to clean-architecture vocabulary
without changing a line of code.

### 10d. No architecture at all — use a library

There are mature property-based testing libraries for Scala
already (ScalaCheck, scalacheck-effect, ZIO Test's property testing
support). Why not extend one of them with a coverage-guided
generator and call it a day?

Considered. Rejected for three reasons. First, none of those
libraries currently exposes the generator in a way that a guided
strategy could hook into; they assume the generator is a pure
function of the random seed. Retrofitting feedback into them would
mean changing internal APIs we don't control. Second, the thesis
explicitly compares a coverage-guided generator to the random
baseline; running both inside the same harness, with the same
measurement code, is essential for a clean comparison. Third, the
project is also an exercise in architectural design — the thesis
benefits from the explicit structure.

---

## 11. When ports and adapters is the wrong call

It's worth saying out loud, because the pattern has a faintly culty
reputation in some circles.

**One-off scripts.** If you'll run it twice and throw it away, just
write the script. The cost of ports and adapters is paid in files,
naming, and indirection — none of which the script ever recoups
because it never grows.

**Code with no alternatives.** If a given piece of logic genuinely
has one and only one possible implementation, and there's no
testing reason to abstract it, don't. The port-without-a-purpose
is a real anti-pattern; it adds bureaucracy without any of the
swappability that justifies the cost.

**Tiny CRUD apps.** A web app that's mostly "read a row, render a
template, write a row back" has nothing structural to abstract.
Layered architecture or even no architecture is fine. Trying to
hexagonalise it ends up with a port per database table, which is
silly.

**Code where the boundary is wrong.** Sometimes the most natural
abstraction *isn't* "a port representing a side-effect-having
thing outside the application". Sometimes it's a state machine, a
pipeline of transformations, or an event log. Forcing every
project into ports and adapters because the pattern is fashionable
is its own kind of bad taste.

---

## 12. Extension points

Where to plug new behaviour without touching the use case:

| You want to add…                              | Touch                                                              |
|-----------------------------------------------|--------------------------------------------------------------------|
| A new Scala construct (`try`, `for`)          | One case in the AST builder + one variant in `BranchTree`          |
| A new input type (`String`, `case class`)     | Generalise `InputGenerator` / `TestRunner` over a type parameter   |
| A real coverage-guided generator              | New `InputGenerator` adapter; wire in the driving adapter          |
| A new coverage source                         | New `SourceCoverageReader` and/or `BranchCoverageTracker` adapter  |
| A new output format (HTML, Prometheus, …)     | New `CoverageReportWriter` adapter                                 |

The driving adapter (`FileSystemTestRunner`) is the only place that
needs to know about the new concrete class. Everything else stays
the same.

---

## 13. Summary

For this thesis specifically:

- The reason to use ports and adapters is that the thesis is a
  comparison of two strategies, and the pattern reduces "swap a
  strategy" to "swap an adapter".

- The reason it might be over-engineering is that four of the five
  driven ports will probably always have one adapter.

- The honest middle ground is: the cost of those four
  rarely-swapped ports is small (a trait definition each, no extra
  runtime cost, no extra mental cost once you've internalised the
  pattern), and the uniformity benefits of "everything is behind a
  port" outweigh the saved trait definitions.

- The single most important port — `InputGenerator` — has two
  adapters today (random and a placeholder for guided), and the
  whole pattern earns its keep in that one place.

If you take only one thing away from this document, take this:

> *Ports and adapters is a pattern for changeable software. We use
> it because the central question of the thesis — does guided beat
> random? — requires us to change one thing and keep everything
> else identical. The pattern is the cheapest way we know to make
> that change a one-line edit.*

Everything else is implementation detail.

---

*Diagrams above are generated by the Python scripts under
[`docs/diagrams/`](diagrams/). Run `make diagrams` to regenerate
them; see [`Makefile`](../Makefile) for the full list of commands.*
