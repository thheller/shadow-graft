# shadow-graft

[![Clojars Project](https://img.shields.io/clojars/v/com.thheller/shadow-graft.svg)](https://clojars.org/com.thheller/shadow-graft)

shadow-graft facilitates the calling of client-side functions from the server-side generated HTML.

The "graft" name is inspired by a similar function in horticulture.

> Grafting is the act of placing a portion of one plant (bud or scion) into or on a stem, root, or branch of another (
> stock) in such a way that a union will be formed and the partners will continue to grow. The part of the combination
> that provides the root is called the stock; the added piece is called the scion.

https://www.britannica.com/topic/graft

## Motivation

The concept is that the server generates the "root/stock" HTML tree and leaves markers on specific positions in that tree. The client-side can then implement "scions" which are meant to enhance/grow the actual DOM tree. Basically giving the server the ability to call client side functions.

It provides a good starting point for any [PWA](https://web.dev/progressive-web-apps/), whether you use something like and [Island Architecture](https://jasonformat.com/islands-architecture/) or a full Single Page App.

## How to use

### Client Side

```clojure
(ns demo.app
  (:require
    [shadow.graft :as graft]
    [cljs.reader :as reader]))

;; these are silly I know ;)
(defmethod graft/scion "disappearing-link" [opts link]
  (.addEventListener link "click"
    (fn [e]
      (.preventDefault e)
      (.remove link))))

(defmethod graft/scion "just-log-data" [opts container]
  (js/console.log "just-log-data" opts container))

(defn init []
  (graft/init reader/read-string))
```

The `init` fn should be called by shadow-cljs `:init-fn` in the build config. The first argument is the reader function
used to parse data generated by the server. We'll use EDN as an example here. You can provide and function you want (
eg. `js/JSON.parse` or transit).

### Server Side

Since there are a variety of ways to manage state on the server I'm going to use the simplest example here. But the server-side parts are meant to be integrated into whatever state mechanism you use (eg. mount, component, integrant,
etc.).

For demo purposes I'm gonna use a simple var. Since there really is no need to cleanup its state this is fine.

```clojure
(ns demo.server
  (:require
    [hiccup.core :refer (html)]
    [shadow.graft :as graft]))

;; using EDN as the data format via pr-str, could be anything
(def graft (graft/start pr-str))

(defn sample-server-component [req]
  (html
    [:a {:href "http://google.com"} "google.com"]
    (graft "disappearing-link" :prev-sibling)
    ;; or, slightly more verbose
    (graft/add graft "disappearing-link" :prev-sibling)
    ))

...
```

`graft` here takes at least two arguments. The id of the scion, which is also the dispatch value used in the client side `graft/scion` multi-method. The second argument specifies which DOM element this function should be targeting. In
this case it targets the previous sibling DOM element.

Valid values here include

- `:none` - no reference node
- `:self` - the node created for the placeholder itself
- `:parent` - the DOM element parent containing the placeholder
- `:prev-sibling` or `:next-sibling`

The third argument is the more interesting one. Many things will require passing data to the client and that is where this goes.

```clojure
(defn sample-hiccup-with-data [req]
  (html
    [:div
     [:h1 "nonsense example"]
     (graft "just-log-data" :parent {:hello "world"})]))
```

The graft on the server-side generates simple script tags, eg.

```html
<script type="shadow/graft" data-id="just-log-data" data-ref="parent">
optional-base64-encoded-text
</script>
```

They are not visible and are not further interpreted by browsers until our code looks for them. They just represent data. For extra security the data is encoded via Base64.

This is intentionally simple so that any server can generate this and still hand off data to the client this way. The default implementation assumes a CLJ server but that is by no means necessary. Anything that is capable of generating this kind of script tag is viable.

## A Closer To Real-World Example

In a typical reagent/re-frame app you'll have something like

```clojure
(ns graft-example.core
  (:require
    [reagent.dom :as rdom]
    [re-frame.core :as re-frame]
    ...
    ))

(defn ^:dev/after-load mount-root []
  (re-frame/clear-subscription-cache!)
  (let [root-el (.getElementById js/document "app")]
    (rdom/unmount-component-at-node root-el)
    (rdom/render [views/main-panel] root-el)))

(defn init []
  (re-frame/dispatch-sync [::events/initialize-db])
  (mount-root))
```

With a `<div id="app">` generated by the server somehow.

Instead, this now becomes

```clojure
(ns graft-example.core
  (:require
    [reagent.dom :as rdom]
    [re-frame.core :as re-frame]
    [shadow.graft :as graft]
    [cljs.reader :as reader]
    ...
    ))

(defmethod graft/scion "app"
  [{:keys [data props] :as opts} root-el]
  ;; runs once
  (re-frame/dispatch-sync [::events/initialize-db data])

  ;; runs on init and again for each hot-reload
  (graft/reloadable
    (re-frame/clear-subscription-cache!)
    (rdom/unmount-component-at-node root-el)
    (rdom/render [views/main-panel props] root-el)))

(defn init []
  (graft/init reader/read-string))
```

Looks somewhat similar, but we gained the ability to pass data into our `::events/initialize-db` event and can pass props to the root component.

It also becomes much easier to add more scions in case you want to go for more of [Island Architecture](https://jasonformat.com/islands-architecture/) type setup and not purely a SPA.

For example you could add a `"nav"` scion, that targets and enhances the HTML generated by the server. And a `"configure` scion that sets up some shared state for later maybe?

```clojure
(defmethod graft/scion "configure"
  [{:keys [current-user locale]} _]
  ...)

(defmethod graft/scion "nav"
  [_ root-el]
  ...)
```

On the server this all looks something like 

```clojure
(ns graft-example.server
  (:require
    [hiccup.core :refer (html)]
    [hiccup.page :refer (html5)]
    [shadow.graft :as graft]
    ))

(def graft (graft/start pr-str))

(defn ui-page []
  (html5 {}
    [:head
     [:link {:rel "preload" :as "script" :href "/js/main.js"}]
     
     [:title "My Page"]
     (graft "configure" :none
       {:current-user "thheller"
        :locale "de_DE"})]

    [:body
     [:nav
      [:ul
       [:li "Page 1"]
       [:li "Page 2"]]
      (graft "nav" :parent)]

     [:div
      (graft "app" :parent
        {:data (get-init-data)
         :props {:hello "world"}})]
     
     [:script {:type "text/javascript" :src "/js/main.js" :defer true}]]))
```

I simplified the non-graft things a little for brevity. The point is that the server just generates some HTML and leaves some graft markers for later use.

Note that the graft points are all traversed in the DOM (depth-first) order. They all execute when the script `init` fn is called. Since the order is guaranteed the `"configure"` scion runs first and all later scions can rely on `configure` having executed first. Of course if that ends up doing something async, you'll need to coordinate that further yourself.

## Notes

If you have been long around enough in web development you might remember something like `$(".some-element").doStuff()` jquery-style plugins. They are similar in nature, but also made suffered the hardcoded id/class problems and made it difficult to pass data as well. It also had the issue of often looking for stuff that wasn't even on the page, just because it was on 1 or 50, and it was easier to just have the script always look them than to modify the script for that one page.

I have used a [similar method](https://code.thheller.com/blog/web/2014/12/20/better-javascript-html-integration.html) exclusivly for many many years. It was time to create a proper library for this, so I can throw away my old hacky functions and finally have a proper name for the technique.

Also took the time to make this work with multiple `:modules` and generating the necessary info via a [shadow-cljs](https://github.com/thheller/shadow-cljs) build hook. Docs on that to follow.