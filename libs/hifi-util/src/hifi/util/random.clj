(ns hifi.util.random
  (:import
   [java.security SecureRandom]))

(set! *warn-on-reflection* true)

(defn make-reseeding-secure-random
  "Creates a stateful function that manages a SecureRandom instance,
    automatically reseeding every `n` invocations to supplement entropy.

    Returns a zero-argument function that returns the managed SecureRandom
    instance after conditionally reseeding it."
  ([]
   (make-reseeding-secure-random 1024))
  ([n]
   (let [srng (SecureRandom/getInstanceStrong)
         counter (volatile! 0)]
     (fn []
       (if (< @counter n)
         (vswap! counter inc)
         (do
           (vreset! counter 0)
           (.setSeed srng (.generateSeed srng 8))))
       srng))))

(def ^:private rsrng*
  "Thread-local storage for reseeding SecureRandom function instances."
  (proxy [ThreadLocal] []
    (initialValue [] (make-reseeding-secure-random))))

(defn secure-random
  "Returns an auto-reseeding thread-local java.security.SecureRandom instance.

    Each thread maintains its own isolated SecureRandom that is automatically
    reseeded every 1024 calls to supplement entropy and maintain cryptographic
    strength over long-running processes.


    Usage notes:
    - May block while gathering system entropy
    - Suitable for cryptographic operations requiring high-quality randomness
    - The choice and parameters of the backing implementation are determined by
      Java security properties (e.g., securerandom.strongAlgorithms in java.security)."
  ^java.security.SecureRandom
  []
  ((.get ^ThreadLocal rsrng*)))
