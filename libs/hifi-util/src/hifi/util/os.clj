(ns hifi.util.os
  "Operating system and architecture detection utilities.
  
  Provides platform detection functions and constants for identifying the
  current operating system and CPU architecture at runtime. All detection
  results are memoized for performance.")

(def os-names
  "Map of regex patterns to operating system keywords.
  
  Patterns match against the `os.name` system property to identify the
  current operating system. Patterns are case-insensitive."
  {#"(?i)^mac os x$|^darwin$"               ::macos
   #"(?i)^windows"                          ::windows
   #"(?i)linux"                             ::linux
   #"(?i)freebsd"                           ::freebsd
   #"(?i)^sunos$|solaris"                   ::solaris
   #"(?i)openbsd"                           ::openbsd})

(def arch-names
  "Map of regex patterns to CPU architecture keywords.
  
  Patterns match against the `os.arch` system property to identify the
  current CPU architecture. Patterns are case-insensitive."
  {#"(?i)^x86_64$|^amd64$"                  ::x86_64
   #"(?i)^x86$|^i[3-6]86$"                  ::x86
   #"(?i)^aarch64$|^arm64$"                 ::aarch64
   #"(?i)^arm(v\d+)?(l)?$"                  ::arm
   #"(?i)^ppc64(le)?$"                      ::ppc64
   #"(?i)^s390x$"                           ::s390x
   #"(?i)^riscv64$"                         ::riscv64
   #"(?i)^sparc(v9)?$"                      ::sparc})

(defn- lookup
  "Exact match in m for k, otherwise first regex-key whose pattern matches k."
  [m k]
  (or (get m k)
      (some (fn [[pat v]] (when (re-find pat k) v)) m)))

(def os
  "Returns the current operating system as a keyword.
  
  Detects the OS by matching the `os.name` system property against known
  patterns in `os-names`. Returns one of `::macos`, `::windows`, `::linux`,
  `::freebsd`, `::openbsd`, or `::solaris`. Returns `nil` if the OS cannot
  be determined. Result is memoized."
  (memoize (fn [] (lookup os-names (System/getProperty "os.name")))))

(def architecture
  "Returns the current CPU architecture as a keyword.
  
  Detects the architecture by matching the `os.arch` system property against
  known patterns in `arch-names`. Returns one of `::x86_64`, `::x86`,
  `::aarch64`, `::arm`, `::ppc64`, `::s390x`, `::riscv64`, or `::sparc`.
  Returns `nil` if the architecture cannot be determined. Result is memoized."
  (memoize (fn [] (lookup arch-names (System/getProperty "os.arch")))))

(def ^:dynamic *os*
  "Dynamic var holding the current operating system keyword.
  
  Defaults to the detected OS via `os`. Rebind this var to simulate different
  operating systems in tests. See [[with-os]]."
  nil)

(def ^:dynamic *architecture*
  "Dynamic var holding the current CPU architecture keyword.
  
  Defaults to the detected architecture via `architecture`. Rebind this var
  to simulate different architectures in tests. See [[with-arch]]."
  nil)

(defn windows?
  "Returns `true` if running on Windows, `false` otherwise."
  []
  (= ::windows (or *os* (os))))

(defn linux?
  "Returns `true` if running on Linux, `false` otherwise."
  []
  (= ::linux (or *os* (os))))

(defn macos?
  "Returns `true` if running on macOS, `false` otherwise."
  []
  (= ::macos (or *os* (os))))

(defn aarch64?
  "Returns `true` if running on ARM64/AArch64 architecture, `false` otherwise."
  []
  (= ::aarch64 (or *architecture* (architecture))))

(defmacro with-os
  "Executes `body` with `*os*` bound to `os-keyword`.
  
  Useful for testing platform-specific behavior without requiring actual
  platform changes.
  
  Example:
  ```clojure
  (with-os ::windows
    (windows?)) ;=> true
  ```"
  [os-keyword & body]
  `(binding [*os* ~os-keyword]
     ~@body))

(defmacro with-arch
  "Executes `body` with `*architecture*` bound to `arch-keyword`.
  
  Useful for testing architecture-specific behavior without requiring actual
  architecture changes.
  
  Example:
  ```clojure
  (with-arch ::aarch64
    (aarch64?)) ;=> true
  ```"
  [arch-keyword & body]
  `(binding [*architecture* ~arch-keyword]
     ~@body))
