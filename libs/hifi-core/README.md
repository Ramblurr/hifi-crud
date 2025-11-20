# hifi-core

> hifi-core is TODO


:hifi/components - set of namespaces to load hifi components from

---

component groups - donut uses component groups, (components can't be at the top level of the system), this is great for organization

child components the key below also corresponds to the name in the config edn. So the system map

{::ds/defs { :env {...} :hifi/web { :hifi.web/server { :start, :stop, :config COMPONENT DEF } }}, would inject (get env :hifi.web/server) into the :config key


component group :hifi/web

:hifi.web/server - the http server component, opens ports, handles connections etc

:hifi.web/application - the root ring handler that wraps the router and other ring handlers, there can be only one

:hifi.web/router-options -


component group :hifi/middleware - the middleware registry

dynamic component/children of middleware-components
