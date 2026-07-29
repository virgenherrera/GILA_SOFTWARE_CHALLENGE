(ns ecommerce.cart.middleware
  (:require [buddy.sign.jwt :as jwt]
            [clojure.string :as str]))

(defn sign-cart-id
  "Sign a cart-id UUID into a JWT token using HMAC-SHA256."
  [secret cart-id]
  (jwt/sign {:cart-id (str cart-id)} secret))

(defn unsign-cart-id
  "Unsign a JWT token and return the cart-id UUID, or nil if tampered/invalid."
  [secret token]
  (try
    (java.util.UUID/fromString (:cart-id (jwt/unsign token secret)))
    (catch Exception _ nil)))

(defn- parse-cookie-header
  "Parse a Cookie header string into a map of name->value pairs.
   Example: \"cart_id=abc123; other=xyz\" -> {\"cart_id\" \"abc123\" \"other\" \"xyz\"}"
  [cookie-str]
  (when (and cookie-str (not (str/blank? cookie-str)))
    (into {}
          (keep (fn [pair]
                  (let [trimmed (str/trim pair)
                        idx (str/index-of trimmed "=")]
                    (when (and idx (pos? idx))
                      [(str/trim (subs trimmed 0 idx))
                       (str/trim (subs trimmed (inc idx)))]))))
          (str/split cookie-str #";"))))

(defn- build-set-cookie
  "Build a Set-Cookie header value for the signed cart-id token."
  [secret cart-id]
  (let [token (sign-cart-id secret cart-id)]
    (str "cart_id=" token
         "; HttpOnly"
         "; SameSite=Strict"
         "; Path=/api"
         "; Max-Age=2592000")))

(defn wrap-cart-cookie
  "Cart cookie middleware. Takes cookie-secret, returns a reitit-compatible middleware.
   On request: parses cart_id cookie, unsigns it, attaches :cart-id to request (or nil).
   On response: if response has :set-cart-cookie metadata, adds Set-Cookie header."
  [cookie-secret]
  (fn [handler]
    (fn [request]
      (let [cookies (parse-cookie-header (get-in request [:headers "cookie"]))
            cart-id (when cookie-secret
                      (some-> (get cookies "cart_id")
                              (->> (unsign-cart-id cookie-secret))))
            response (handler (assoc request :cart-id cart-id))
            new-cart-id (:set-cart-cookie (meta response))]
        (if (and new-cart-id cookie-secret)
          (-> response
              (assoc-in [:headers "Set-Cookie"] (build-set-cookie cookie-secret new-cart-id)))
          response)))))
