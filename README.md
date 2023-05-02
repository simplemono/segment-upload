# Segment Upload

Uploading a file is straightforward as long as you have a fast and stable
connection. However, this is often not the case, so you need a reliable way
to retry the upload. Naturally, you want to avoid uploading a large file again.
Therefore you split the file into 1 MB segments, for example, and upload a few
segments in parallel.

Often the mechanism to compose the segments on the server side into one file
again is quite complex. Especially if you define a place, meaning a file path,
where the file should be stored. The path is often associated with the application semantics (like `/images/my-new-background-image.jpg`).

The design approach of this mini-framework is to consider 'ideal world', where
the problem of unstable and slow connections does not exist. From there, we make
trade-offs to adapt the solution to the conditions of the real world. For example, in this
'ideal world' it would be possible to use one HTTP POST or PUT to upload an
arbitrarily large file with a single request. Furthermore, the request handler could place the
file contents directly in the desired location.

The focus is to provide a solution to overcome a potentially slow and unstable
connection of an end-user that tries to upload a file. As soon as the file resides on fast and reliable storage, like S3 or Google Cloud Storage, the
server can download the file from there with a single GET request.

The mini-framework allows uploading segments of a file to such storage (only
Google Cloud Storage at the moment) or the local file system of the server.
However, 'file' is misleading since the segments are just binary
data (blob) without a filename. Instead, everything is named with a UUID. After
all segments have been uploaded, they can be composed in a second step into a blob
named with a UUID. The compose step returns an URL that the client
can pass to the server's API. It is up to the server what it does with this
binary data. Thereby neither the purpose of the binary data nor any details of
the server API are intertwined with this mini-framework.

Another complexity that is avoided is authentication. Uploading and composing
segments are public endpoints. However, you can add rate limiting and
authentication around the Ring handlers of this mini-framework. The
authentication and authorization of Google Cloud Storage are still active. The
server will provide a signed URL to the client that allows the client to upload
the segment content directly to Google Cloud Storage. Using a signed URL would
enable us to enforce rate limiting and authentication, but it also avoids making the
Google Cloud Storage bucket public. With a public bucket, you would need to pay
attention that you don't allow listing the objects inside the bucket. Otherwise,
it would be possible to read the data of all uploaded segments. With the signed
URL the (random) UUID of a segment can act as a secret. Last but not least,
Google Cloud Storage's lifecycle feature can automatically delete all
files older than 24 hours, for example.

The upload of a segment is initialized with a PUT request to:

```
HTTP PUT /segment-upload/upload/{UUID}
```

Please generate a random UUID and replace the `{UUID}`
([JS](https://developer.mozilla.org/en-US/docs/Web/API/Crypto/randomUUID),
[Java](https://docs.oracle.com/javase/8/docs/api/java/util/UUID.html#randomUUID--)).

The response will be a [HTTP
307](https://developer.mozilla.org/en-US/docs/Web/HTTP/Status/307), where the
location header contains a signed URL. Some HTTP clients, like JavaScript's
`fetch` or `XHR` automatically follow the redirect and repeat the PUT request on
the URL returned in the location header. Others, like curl do not do this
automatically:

``` sh
curl -L -v -X PUT -d "hello" "http://example.com/segment-upload/upload/`uuid`"
```

Here the `-L` flag was added to activate this behavior.

After all segments have been uploaded successfully, the compose endpoint should
be used:

```
HTTP POST /segment-upload/compose
```

It expects a JSON body:

```json
{"uuids" : ["0f4864e0-e8e5-11ed-894f-13e627f35e14",
            "b48fede2-e8e5-11ed-8630-f3aa81584342"]}
```

The response will contain a JSON body as well:

```json
{"url" : "{a signed URL}"}
```

The returned signed URL will be valid for 10 minutes, enough time to use it with another server API. An API consumer would also have the
option to use his server, S3, Google Cloud Storage, etc., to host the file
contents. Thereby the API is not entangled with the upload mechanism. One neat
detail is that Google Cloud Storage offers an API call to compose objects in the
same bucket. Thereby these objects do not have to be downloaded to compose them.

## ClojureScript client

The subfolder `upload-manager` contains an upload-manager implemented in
ClojureScript that follows the contracts of the segment-upload (described
above). You can implement your UI for it, or you can use the component in the
`upload-manager-ui` subfolder, which is a UI implemented using Reagent and
TailwindCSS. The `upload-manager-ui-devcard` contains a demo that shows the
`upload-manager` and the `upload-manager-ui` in combination.
