# What is this project?

A non-technical introduction. If you've never written software in your
life, you should be able to read this and come away knowing what we are
building, why it matters, and what is in the repository today.

There is no code in this document. There are no acronyms you need to
look up. The only word you may not know is *branch*, and we'll define
it the moment we use it.

---

## 1. The one-sentence version

This project is a research prototype that explores a smarter way to
**test computer programs** by letting the program "watch itself" being
tested and learning from what it sees.

That sentence will make a lot more sense after the next few pages.

---

## 2. Why we test software at all

Every program is full of small decisions. A piece of code might say:
"if the user's age is over 18, allow them in; otherwise, refuse." Or:
"if the temperature is above 30 degrees, turn on the fan; otherwise,
leave it off."

Each of these decisions is a **branch** — two different paths the
program might take depending on the situation. A real program has
thousands of branches.

The job of testing is to make sure every branch behaves correctly.
That sounds easy, but it isn't, because:

- There are too many branches to test by hand.
- Some branches are only triggered by very specific inputs.
- Programs change all the time, so tests have to keep up.

If even one branch is broken, the user notices: the fan turns on at
the wrong temperature, the underage user gets through, the bank
transfers the wrong amount. So we want to test as many branches as
possible, as thoroughly as possible, with as little human effort as
possible.

---

## 3. How people test software today

There are two common approaches.

### 3a. Example-based testing

Most testing today works like this: a programmer thinks of a few
specific situations, writes down what the right answer should be, and
checks the program against each one.

For instance, if we wrote a function that doubles a number, we might
test it with:

- give it `2`, expect `4`
- give it `0`, expect `0`
- give it `-7`, expect `-14`

This works. But it only tests the cases the programmer thought of.
If they forgot to test very big numbers (and the program quietly
breaks for those), no one finds out until a real user runs into the
bug.

### 3b. Property-based testing

Property-based testing turns the idea around. Instead of writing
specific examples, you describe a **rule** the program should always
obey, and then you let the computer try lots of different inputs to
see if the rule ever breaks.

For our doubling example, the rule could be: "the output is always
twice the input." The computer would then try hundreds of random
numbers — `42`, `-1`, `999999`, `0`, `-50000` — and check the rule
each time.

The advantage is obvious: the computer thinks of inputs the
programmer would never have thought of. The disadvantage is what we
look at next.

---

## 4. The blind spot of random testing

Random inputs are good at finding *common* mistakes. They are not so
good at finding *rare* ones.

Imagine a function that says:

> "If the number is exactly `42`, give back the string `the answer`.
> Otherwise, give back `something else`."

The branch that gives back `the answer` is only reached when the
input is exactly `42`. If you generate a hundred random numbers
between negative billions and positive billions, you will almost
certainly never hit `42`. The "answer" branch is never tested at all —
but the property-based testing tool happily reports that it tried
100 inputs and found no bugs.

This is the **blind spot**: random testing can completely miss tricky
branches and not even know it missed them.

In our own example code (the small piece we use to test the
framework), we have a method called `classify97`. It returns
`"lucky"` only when the number, divided by 97, leaves a remainder of
13. The probability of that happening on a uniformly chosen integer
is roughly 1 in 97 — about 1 %. With 100 random inputs the `"lucky"`
branch may simply never be hit. And in our experiments, it isn't.

So the random tool happily says "100 inputs run, no problems." But
half the code is untested.

---

## 5. The missing piece: coverage

If we want to fix the blind spot, we first have to *see* it. We need
some way of knowing, for each test run, which parts of the code were
actually executed and which weren't.

This is called **coverage**. It's an old, well-understood idea: while
the program is running, an extra layer of "watching" software keeps
track of which lines were touched.

Think of it like a map of a museum. Every time a visitor walks into a
room, we tick that room off the map. At the end of the day, we look
at the map: if some rooms are never ticked, we know visitors never
walked through them.

Coverage gives us the same kind of map for code: at the end of a test
run, we can see which branches were exercised and which weren't.

---

## 6. The big idea: use the map to guide the next visit

Here's where this thesis comes in. If we have a *map* showing which
branches we haven't reached, we don't have to keep guessing
randomly. We can ask: *"What kind of input would be likely to reach
that unvisited branch?"*

This is the core idea of **coverage-guided** property-based testing.
It's well known in some corners of software security (a famous tool
called AFL has used this approach for years to find bugs in C
programs), but it's not yet common in everyday testing tools, and
especially not in the Scala world.

The goal of this thesis is to build a small prototype that:

1. Runs random tests against a piece of Scala code.
2. Watches which branches were exercised.
3. Uses that information to make future tests smarter, so the rare
   branches get reached too.

Step 1 and 2 are what the code does today. Step 3 is the next phase.

---

## 7. What this prototype does today

If you run the project right now, here is what happens, in plain
English:

1. We pick a small target method — say, our `classify97` example.
2. We ask a random-number generator for 100 different integers.
3. For each integer, we feed it into the method.
4. As the method runs, an extra layer watches and records *which
   lines of code were executed* on that run.
5. We collect all 100 observations into a single picture of how well
   the method's branches were exercised over the whole session.
6. We write the result to disk in five complementary forms (see
   section 9).

The "smart" part — using the recorded coverage to bias the random
generator toward unexplored branches — is not yet implemented. The
groundwork is in place, though: the random generator already
receives all the information a smart generator would need. Today it
just throws that information away.

So you can think of the project as a **measuring stick**: we've built
the part that observes and reports. The "decide what to do next"
part is the next step.

---

## 8. The little example we test

To demonstrate the framework we wrote three deliberately silly
methods that all take an integer and return a string. They are
designed to *highlight* the blind-spot problem.

**`classify97`** returns `"lucky"` if the number's remainder when
divided by 97 is exactly 13; otherwise it returns `"normal"`. The
`"lucky"` branch is rare — random integers will almost never hit it.

**`isPositive`** returns `"positive"` if the number is greater than
zero, and `"non-positive"` otherwise. Both branches are easy to
reach: roughly half the random integers fall on each side.

**`category`** is a small chain of decisions: if the number is
greater than 100, it asks whether the number divides by 7; if not,
it asks whether the number is exactly 42; and so on. It has eight
branches in total, and one of them — the branch that returns
`"answer"` — is reached only when the input is exactly 42, which
basically never happens for random integers.

These three methods aren't important in themselves. They're a
playground: easy enough to read at a glance, but rich enough that we
can clearly see "random testing missed this branch."

---

## 9. What comes out the other end

When you run the framework against one of these methods, it writes
five files into a folder named after the method. Here's what each
file is:

**`summary.txt`** — a short human-readable summary. It says how many
inputs were tried, how many branches were covered out of how many
total, when coverage stopped growing, and a per-line breakdown of
which lines of code got exercised. This is the "did the test go
well?" sentence you'd quote in a thesis.

**`coverage.dot`** / **`coverage.svg`** — a tree picture. At the top
sits the method; at the bottom sit the individual branches. Each
branch is coloured green if it was reached and red if it wasn't.
For `classify97`, you'll see the `"lucky"` leaf in red and the
`"normal"` leaf in green — a one-glance picture of the blind spot.

![Hexagonal architecture](images/hexagon.png)

(That's actually the architecture diagram; the coverage tree looks
similar in structure — a branching diagram with coloured leaves.)

**`growth.svg`** — a line chart. The x-axis is "how many inputs we've
tried so far"; the y-axis is "how many branches are covered." For
methods that get fully covered quickly, the line shoots up and then
flattens. For methods with a hard-to-reach branch, the line never
finishes climbing.

**`inputs.csv`** — a spreadsheet-style table with one row per input:
the input value, how many lines it exercised, and which exact lines
those were. Useful if you want to look at the data with a
spreadsheet tool or a small script.

**`coverage.json`** — the same information again, in a structured
form that other programs can parse. Mostly for downstream tooling.

Together, these five files let a researcher say very concretely:
"with random testing alone, this method's coverage saturated at this
point and these branches were missed."

---

## 10. The bigger picture

The thesis is comparing two kinds of testing tools:

- **Random property-based testing**, which is what the world mostly
  uses today.
- **Coverage-guided property-based testing**, which is what this
  thesis prototype is being built to demonstrate.

To make a fair comparison you need both. The current code is the
random baseline plus all the infrastructure (coverage measurement,
reports, plumbing) that the guided version will reuse. The next step
is to write the actual guided strategy and run the same experiments
again, then compare the two side by side.

The expectation — and the part this thesis hopes to demonstrate — is
that the guided version reaches the rare branches in dramatically
fewer inputs than random does, on methods of the shape we showed in
section 8.

---

## 11. Who cares?

People who test software professionally care, because better tools
mean fewer bugs reach real users.

People who research testing methodology care, because coverage-guided
testing is one of a small handful of techniques that consistently
outperform pure random on certain kinds of code, and bringing it to
new languages (here, Scala) is genuinely useful.

People who write software in Scala care, because no widely-used
coverage-guided property-based testing tool exists for the language
today.

And from the thesis-writing point of view, the project is a vehicle
for studying a clear, well-bounded question: *does coverage feedback
make random testing measurably better, and by how much, on small
methods with branchy structure?*

That's it. That's the whole project, in one read.
