const express = require("express");
const router = express.Router();
const fs = require("fs");
const path = require("path");
const { JSDOM } = require('jsdom');
const createDOMPurify = require('dompurify');
const markdown = require("markdown-wasm");
const db = require("../user_modules/db.cjs");

const window = new JSDOM('').window;
const DOMPurify = createDOMPurify(window);

router.get('/', (req, res) => {
  db.showTables("blog_tags")
  .then(tables => {
    res.render("posts/posts", { tags: tables, uid: req.session.uid });
  })
  .catch(console.log);
});

router.get("/archive", (req, res) => {
  db.getOrderedData("blog_posts", "post", "date", "desc")
  .then(posts => {
    res.render("posts/archive", { posts: posts, uid: req.session.uid });
  })
  .catch(console.log);
});

router.route("/new")
.get((req, res) => {
  if (req.session.uid) {
    getID()
    .then(pid => {
      res.redirect(`/posts/editor/${pid}`);
    })
    .catch(console.log);
  } else {
    req.session.returnTo = "/posts/new";
    res.redirect("/users/login");
  }
});

router.route("/editor/:pid")
.get((req, res) => { 
  if (req.session.uid) {
    db.getValueData("blog_posts", "post", "pid", req.params.pid)
    .then(post => {
      if (post[0]) {
        res.render("posts/editor", { author: post[0].author, post: post });
      } else {
        res.render("posts/editor", { author: req.session.username });
      }
    })
    .catch(console.log);
  } else {
    req.session.returnTo = `/posts/editor/${req.params.pid}`;
    res.redirect("/users/login");
  }
})
.post((req, res) => {
  if (req.body.title && req.body.body && req.body.banner && req.session.uid) {
    db.getValueData("blog_posts", "post", "pid", req.params.pid)
    .then(post => { 
      let author, uid;

      if (post[0]) {
        if (post[0].uid == req.session.uid || req.session.admin) {
          author = post[0].author;
          uid = post[0].uid;
        } else {
          throw "forbidden";
        }
      } else {
        author = req.session.username;
        uid = req.session.uid;
      }

      uploadPost(req.body.title, req.body.body, req.body.tags, req.body.banner, author, uid, req.params.pid)
      .then(() => res.redirect(`/posts/${req.params.pid}`))
      .catch(console.log);
    })
    .catch(err => res.sendStatus(403));
  } else {
    res.sendStatus(400);
  }
});

router.get("/pid", (req, res) => {
  res.send({ pid: req.session.pid });
});

router.route("/:pid")
.get((req, res) => {
  db.getValueData("blog_posts", "post", "pid", req.params.pid)
  .then(post => {
    res.render("posts/post", {
      title: post[0].title,
      body: markdown.parse(post[0].body),
      tags: post[0].tags.split(','),
      banner: post[0].banner,
      author: post[0].author,
      pid: post[0].pid,
      uid: post[0].uid,
      date: formatDate(post[0].date)
    });
  })
  .catch(err => {
    res.status(404);
    res.render("http/status", {
      code: "404",
      message: `Post with id: ${req.url} not found.`
    });
  })
})
.delete((req, res) => {
  db.getValueData("blog_posts", "post", "pid", req.params.pid)
  .then(post => {
    deletePost(post, req.session.uid, req.session.admin)
    .then(() => res.sendStatus(200))
    .catch(err => res.sendStatus(403));
  })
  .catch(err => res.sendStatus(404));
});

async function uploadPost(title, body, tags, banner, author, uid, pid) {
  const date = getDate();

  /* DOM sanitization to mitigate XSS */
  title = DOMPurify.sanitize(title);
  body = DOMPurify.sanitize(body);
  tags = DOMPurify.sanitize(tags).toLowerCase();
  banner = DOMPurify.sanitize(banner);

  await db.sendData("blog_posts", "post",
    ["title", "body", "tags", "banner", "author", "uid", "pid", "date"],
    [title, body, tags, banner, author, uid, pid, date],
    replace = true);
  
  await uploadTags(tags, pid);

  return;
}

async function uploadTags(tags, pid) {
  if (tags) {
    tags = tags.split(',');

    for (var i=0; i<tags.length; ++i) {
      tags[i] = tags[i].trim();
      try {
        await db.createTable("blog_tags", tags[i], "pid", "char(8)");
      } catch (err) {
        console.log(err);
      } finally {
        await db.sendData("blog_tags", tags[i], "pid", pid, replace=true);
      }
    }

    return tags;
  } else {
    return null;
  }
}

async function getID() {
  let idGen = "";
  const charPool = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
  for (var i=0; i<8; i++) {
    // concatenate pseudo-random position in charPool
    idGen += charPool.charAt(Math.floor(Math.random() * 62));
  }

  const ids = await db.getColumnData("blog_posts", "post", "pid");
  // re-run getID() if idGen is present in database
  if (ids.some(e => e.id === idGen)) {
    return getID();
  } else {
    return idGen;
  }
}

function getDate() {
  // grab UTC date and convert to ISO format
  const date = new Date().toISOString().slice(0, 10);
  return date;
}

function formatDate(dateObj) {
  const options = { year: "numeric", month: "long", day: "numeric"};
  const date = dateObj.toLocaleDateString(undefined, options);

  return date;
}

async function deletePost(post, uid, admin) {
  if (post[0].uid === uid || admin) {
    if (post[0].tags) {
      const tags = post[0].tags.split(',');

      for (var i=0; i<tags.length; ++i)
        await db.dropValueData("blog_tags", tags[i], "pid", post[0].pid);
    }
    
    db.dropValueData("blog_posts", "post", "pid", post[0].pid);
    fs.rmSync(path.resolve(__dirname, `../public/media/pid/${post[0].pid}`), { recursive: true });
    
    return "post deleted";
  } else {
    throw "forbidden";
  }
}

module.exports = router;