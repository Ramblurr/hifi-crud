<div align="center">
  <img width="400" src="docs/logo.svg" alt="hificrud">
</div>

# HIFI CRUD

> **H**yperlith **I**s **F**or **I**mmediate **CRUD** - An exploration of FC/IS and server driven web applications in Clojure

This is an experiment in building backend-driven web apps where `view = f(state)` is strictly enforced.

It is built on top of [`hyperlith`](https://github.com/andersmurphy/hyperlith), the opinionated fullstack [Datastar](https://data-star.dev/) framework. It extends its opinions in radical directions.

*What is this for?* HIFI CRUD is an exploration of *simple* yet scalable ways to build business applications. By "business" applications I mean not only back-office dashboards, admin apps, but also customer-facing B2B applications with zero to (tens of) thousands of users.

*Who is this for?* HIFI CRUD explores how solo developers (or very small teams) can leverage Clojure's superpowers to rapidly build applications from day one without a complex stack and without having to throw everything away after the prototype phase.

## Architecture Patterns

HIFI CRUD distinguishes itself from your standard Clojure CRUD web application with the patterns:

- **Immediate mode** - Changes to state re-renders the client.
- **State in the backend** - State that would traditionally (post-2013) live on the client, lives in the backend instead.
- **Strict CQRS** - Commands are strictly separate from the page render functions.
- **Strict FC/IS** - Functional Core/Imperative Shell. Your functional core cannot[^1] cause side effects, all core functions are pure. The shell contains no logic.

Pages are rendered with a single top-level function that takes state as input 

## The View

The view is a pure function of state. What is a view? In HIFI CRUD a view is a page. `/login`, `/dashboard`, `/invoices`, these are all views.

Each view has a single render function whose arguments are *values*. They return a hiccup representing the HTML for the page.

There are no "partials" or "components" in the traditional sense. On every change the entire view is recomputed.

Of course views are just functions calling functions, so you can pull common functionality (dialogs, forms, buttons, etc) into functions accessible from a shared namespace.

Here is the view in the demo application that renders the registration page:

https://github.com/Ramblurr/hifi-crud/blob/e2b1f3fb72814801c770cc2296c7f65f7c8bef21/src/app/auth/register.clj#L72-L118

## The State

There are two sources of backend state:

1. The database
2. Ephemeral per-tab ui state

The example uses [datahike][datahike], an in-process Datomic like database. Why not datalevin? Because the database must implement value semantics, this is non-negotiable [^2].

Entity state, jobs, and sessions, are stored in the database. Every time the database changes, all clients pages are re-rendered and pushed to the client if they changed. This sounds dumb. But rendering the views is fast, and you can always add smart culling later on. This requires a big shift in UX thinking, the page can change underneath the user. You must design appropriately. 

Ephemeral UI state is stored in an in-memory map (atom), this is useful for things like modals, popups, and other UI elements that are not part of the page. This state is not persisted to the database, and is not shared between tabs. 

I waffle on this feature. The state stored in this map could easily be moved to the database, but it doesn't add much complexity to the code and keeps potentially noisy txs out of the database.

## The Command

The command is an action issued by a user, a robot, or a background job with the intent of changing the state of the application (and possible the outside world!). Commands are not a part of the view, they are separate, but their code can be co-located for developer convenience.

Commands are issued to the `/cmd` endpoint which takes a query parameter `cmd` and a body. The body is a JSON object that contains command data. The command is a keyword that identifies the command to be executed. Client commands are triggered by datastar `@post()` actions.

A command handler is a pure function that takes the command data, and possibly other coeffects such as the current database value, the current time, etc. The command handler returns a map of outcomes that describe the side effects that should be performed as a result of the command. The command handler does not change anything! It merely computes what should be changed and conveys that to the shell as data.

Here is the command handler for the command handling registration form submission:

https://github.com/Ramblurr/hifi-crud/blob/e2b1f3fb72814801c770cc2296c7f65f7c8bef21/src/app/auth/register.clj#L55-L65

## The Effect

If the command returns a description of what *should* be done, the effect is the responsible for *doing* it. Every effect has an [effect handler][src-fx]. The effect handler touches the world and makes things happen. 

Examples of effects are:

- `:db/transact` - submits a transaction to the database
- `:d*/merge-signals` - sends a datastar merge-signals SSE event
- `:email/send` - attempts to send an email

Here is the implementation fo the database transaction effect handler:

https://github.com/Ramblurr/hifi-crud/blob/e2b1f3fb72814801c770cc2296c7f65f7c8bef21/src/app/effects.clj#L47-L59

## The Co-effect

~~Sometimes~~ Often a command handler needs a value that is not part of the State and not part of the command data, but is nevertheless required to compute the outcome of the command. Obtaining this value requires an impure operation. 

These values are called co-effects. The command declares what co-effects it requires. They are prepared and passed to the command handler as values along with the State and command data.

Examples of co-effects are:

- The current time
- A random uuid
- The current user

Here is an example of the register command handler requesting co-effects:

https://github.com/Ramblurr/hifi-crud/blob/e2b1f3fb72814801c770cc2296c7f65f7c8bef21/src/app/auth.clj#L28-L33

## The Functional Core

The functional core is the part of the application that is pure. It does not touch the world. Values go into the core, and values come out. The core contains the domain, the business logic, the rules, the invariants, and the functions for manipulating and calculating the state.

The functional core should be where 80+% of your SLOC lives. The functional core can be reasoned about, tested, and understood in isolation.

The entrypoints to the functional core are the View and the Command.

## The Imperative Shell

The imperative shell is the engine that drives the application forward. It mediates with the outside world to accept inputs, pipe them through your functional core, and finally execute effects. The shell contains no knowledge of your domain.

## Notes To Be Expanded Upon In The Future

- This might sound awfully similar to re-frame, yes, it is similar, but also simpler.
- That said, as seen in re-fame land, things can get wild when you start chaining events with other events. It remains to be seen if this is a problem in HIFI CRUD.
- The code calls co-effects "inputs" because in the beginning I did not like the term co-effect. I have since changed my mind, but haven't change the code yet.
- The effect, coeffect, and command definitions are all data. There are no macros (yet?).
  - as a result defining them is a bit verbose, but I want to massage this technique for awhile before adding registration functions or `defxx` macros.

[^1]: well, this is isn't Haskell, if you call `(random-uuid)` in your pure function, that's on you. Shame!
[^2]: ... for me! Please don't take anything personally. If you disagree I would love to hear your thoughts.
[datahike]: https://github.com/replikativ/datahike
[src-fx]: ./src/app/effects.clj
