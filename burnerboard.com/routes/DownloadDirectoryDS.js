const constants = require('./Constants');
const format = require('util').format;
const Datastore = require('@google-cloud/datastore');
const datastore = new Datastore({
  projectId: process.env.PROJECT_ID,
});

exports.addMedia = function (boardID, profileID, mediaType, fileName, fileSize, fileLength, speechCue) {

  return new Promise((resolve, reject) => {

    const localName = fileName.substring(fileName.indexOf(boardID) + boardID.length + 1 + profileID.length + 1);
    const audioKey = datastore.key([mediaType]);
    var newAttributes = "";

    if (mediaType == 'audio') {
      newAttributes = {
        board: boardID,
        URL: format(`${process.env.GOOGLE_CLOUD_BASE_URL}/${process.env.BUCKET_NAME}/${fileName}`),
        localName: localName,
        Size: fileSize,
        Length: fileLength,
        profile: profileID,
        ordinal: null //set later
      };
    }
    else { /* video */
      newAttributes = {
        board: boardID,
        URL: format(`${process.env.GOOGLE_CLOUD_BASE_URL}/${process.env.BUCKET_NAME}/${fileName}`),
        localName: fileName.substring(fileName.indexOf(boardID) + boardID.length + 1),
        SpeachCue: speechCue,
        profile: profileID,
        ordinal: null //set later
      };
    }

    const entity = {
      key: datastore.key(mediaType),
      data: newAttributes,
    };

    const existenceQuery = datastore.createQuery(mediaType)
      .filter('board', '=', boardID)
      .filter('profile', '=', profileID)
      .filter('localName', '=', localName)
      .limit(1);

    const maxOrdinalQuery = datastore.createQuery(mediaType)
      .filter('board', '=', boardID)
      .filter('profile', '=', profileID)
      .order('ordinal', {
        descending: true
      })
      .limit(1);

    datastore.runQuery(existenceQuery)
      .then(results => {
        if (results[0].length > 0)
          throw new Error("the file " + localName + " already exists for board " + boardID + " in profile " + profileID);
        else {
          datastore.runQuery(maxOrdinalQuery)
            .then(results => {

              var maxOrdinal = 0
              if (results[0].length > 0)
                maxOrdinal = results[0][0].ordinal;
              return maxOrdinal;

            })
            .then(function (maxOrdinal) {

              newAttributes.ordinal = maxOrdinal + 1;

              datastore
                .save(entity)
                .then(() => {
                  resolve(mediaType + ` ${localName} created successfully with ordinal ` + newAttributes.ordinal);
                })
                .catch(err => {
                  return reject(err);
                });
            })
            .catch(err => {
              return reject(err);
            });
        }
      })
      .catch(err => {
        return reject(err);
      });
  });
}

exports.createNewBoard = async function (boardID) {

  return new Promise((resolve, reject) => {
    var i = 2;
    datastore
      .save(newElement = {
        key: datastore.key(['board', boardID]),
        data: {
          name: boardID,
          profile: "default",
          isProfileGlobal: false,
        },
      })
      .then(() => {
        return resolve("board " + boardID + " created ");
      })
      .catch(err => {
        return reject(err);
      });
  });
}

exports.createProfile = async function (boardID, profileID, isGlobal) {

  return new Promise((resolve, reject) => {
    var i = 2;
    datastore
      .save(newElement = {
        key: datastore.key(["profile"]),
        data: {
          name: profileID,
          board: boardID,
          isGlobal: isGlobal,
        },
      })
      .then(() => {
        if(isGlobal)
          return resolve("global profile " + profileID + " created");
        else
          return resolve("profile " + profileID + " created for board " + boardID);
      })
      .catch(err => {
        return reject(err);
      });
  });
}

exports.listBoards = async function () {
  return new Promise((resolve, reject) => {

    const boardExists = datastore.createQuery('board')
      .order("name")

    datastore
      .runQuery(boardExists)
      .then(results => {

        results[0].splice(results[0].findIndex(matchesBoard), 1);

        function matchesBoard(board) {
          return board.name === 'template';
        }

        return resolve((results[0]));
      })
      .catch(err => {
        reject(err);
      });
  });
}

exports.listProfiles = async function (boardID) {
  return new Promise((resolve, reject) => {

    var profiles;

    if(boardID == null)
      profiles = datastore.createQuery('profile')
        .order("board")
        .order("name")
    else
      profiles = datastore.createQuery('profile')
        .filter("board","=", boardID)
        .order("name")

    datastore
      .runQuery(profiles)
      .then(results => {
        return resolve(results[0].filter(item => {
          if (item.boardID != 'template')
            return item
        }))
      })
      .catch(err => {
        reject(err);
      });
  });
}

exports.boardExists = async function (boardID) {
  return new Promise((resolve, reject) => {

    const boardExists = datastore.createQuery('board')
      .filter('name', '=', boardID)

    datastore
      .runQuery(boardExists)
      .then(results => {
        return resolve((results[0].length > 0));
      })
      .catch(err => {
        reject(err);
      });
  });
}

exports.profileExists = async function (boardID, profileID, isGlobal) {
  return new Promise((resolve, reject) => {

    const profileExists = datastore.createQuery('profile')
      .filter('name', '=', profileID)
      .filter('board', '=', boardID)
      .filter('isGlobal', '=', isGlobal)

    datastore
      .runQuery(profileExists)
      .then(results => {
        return resolve((results[0].length > 0));
      })
      .catch(err => {
        reject(err);
      });
  });
}

exports.deleteBoard = async function (boardID) {

  return new Promise((resolve, reject) => {

    const deleteBoardQuery = datastore.createQuery('board')
    .filter('name', '=', boardID)

    datastore.runQuery(deleteBoardQuery)
      .then(results => {

        datastore.delete(results[0].map((item) => {
          return item[datastore.KEY];
        }))
        .then(() => {
          resolve("Deleted " + boardID);
        })
        .catch(err => {
          return reject(err);
        });
      });
    })
    .catch(err => {
      return reject(err);
    });

}


exports.deleteProfile = async function (boardID, profileID, isGlobal) {

  return new Promise((resolve, reject) => {

    var deleteProfileQuery;
    var countOfItems;

    if (profileID == null)
      deleteProfileQuery = datastore.createQuery('profile')
        .filter('board', '=', boardID)
        .filter("isGlobal", "=", isGlobal)
    else
      deleteProfileQuery = datastore.createQuery('profile')
        .filter('name', '=', profileID)
        .filter('board', '=', boardID)
        .filter("isGlobal", "=", isGlobal)

    datastore.runQuery(deleteProfileQuery)
      .then(results => {

        countOfItems = results[0].length;

        datastore.delete(results[0].map((item) => {
          return item[datastore.KEY];
        }))
          .then(() => {
            if (isGlobal)
              resolve("Deleted " + profileID + " global profile(s) ");
            else {
              if (profileID == null)
                resolve("Deleted " + countOfItems + " profile(s) for board " + boardID);
              else
                resolve("Deleted " + profileID + " for board " + boardID);
            }
          })
          .catch(err => {
            return reject(err);
          });
      });
  })
    .catch(err => {
      return reject(err);
    });
}


exports.deleteAllBoardMedia = async function (boardID, mediaType) {

  return new Promise((resolve, reject) => {

    const deleteMediaQuery = datastore.createQuery(mediaType)
      .filter('board', '=', boardID)

    datastore.runQuery(deleteMediaQuery)
      .then(results => {

        datastore.delete(results[0].map((item) => {
          return item[datastore.KEY];
        }))
        .then(() => {
          resolve("Deleted " + results[0].length + " " + mediaType + " from " + boardID);
        })
        .catch(err => {
          return reject(err);
        });
      });
    })
    .catch(err => {
      return reject(err);
    });

}


exports.mediaExists = async function (boardID, profileID, mediaType, localName) {

  return new Promise((resolve, reject) => {

    const existenceQuery = datastore.createQuery(mediaType)
      .select('__key__')
      .filter('board', '=', boardID)
      .filter('profile', '=', profileID)      
      .filter('localName', '=', localName)
      .limit(1);

    datastore.runQuery(existenceQuery)
      .then(results => {
        if (results[0].length > 0)
          resolve(true);
        else {
          resolve(false);
        }
      })
      .catch(err => {
        return reject(err);
      });
  });
}

exports.deleteMedia = async function (boardID, profileID, mediaType, localName) {

  return new Promise((resolve, reject) => {

    const deleteQuery = datastore.createQuery(mediaType)
      // .select('__key__')
      .filter('board', '=', boardID)
      .filter('localName', '=', localName)
      .filter('profile', '=', profileID)     
      .limit(1);

    datastore.runQuery(deleteQuery)
      .then(results => {
        if (results[0].length > 0) {
          datastore.delete(results[0][0][datastore.KEY])
            .then(() => {
              resolve("Metadata " + boardID + ":" + profileID + ":" + mediaType + ":" + localName + " deleted.");
            })
            .catch(err => {
              return reject(err);
            });
        }
        else {
          return reject(new Error("Metadata " + boardID  + ":" + profileID + ":" + mediaType + ":" + localName + " did not exist"));
        }
      })
      .catch(err => {
        return reject(err);
      });
  });
}

exports.createNewBoardMedia = async function (boardID, mediaType) {

  return new Promise((resolve, reject) => {

    var entityArray1 = [];

    this.listMedia('template', "default", mediaType)
      .then(results => {

        var resultsPromise = new Promise((resolve, reject) => {
          var mediaArray = results;

          resolve(mediaArray.map((item) => {

            var newElement;

            if (mediaType == 'audio') {
              newElement = {
                key: datastore.key(mediaType),
                data: {
                  board: boardID,
                  URL: item.URL.replace('/template/', '/' + boardID + '/'),
                  localName: item.localName,
                  Size: item.Size,
                  Length: item.Length,
                  ordinal: item.ordinal,
                  profile: item.profile,
                },
              };
            }
            else { /* video */
              newElement = {
                key: datastore.key(mediaType),
                data: {
                  board: boardID,
                  algorithm: item.algorithm,
                  ordinal: item.ordinal,
                  profile: item.profile,
                },
              };
            }

            entityArray1.push(newElement);
          }))
        })
          .then(() => {
            datastore
              .save(entityArray1)
              .then(() => {
                resolve("template copied with " + entityArray1.length + " " + mediaType + " elements");
              })
              .catch(err => {
                return reject(err);
              });
          })
      });
  });
}

exports.listMedia = async function (boardID, profileID, mediaType) {

  return new Promise((resolve, reject) => {

    const mediaList = datastore.createQuery(mediaType)
      .filter('board', '=', boardID)
      .filter('profile', '=', profileID)
      .order('ordinal', {
        descending: false
      });

    datastore
      .runQuery(mediaList)
      .then(results => {
        const mediaList = results[0];

        return resolve(mediaList);

      })
      .catch(err => {
        reject(err);
      });
  });
}

exports.DirectoryJSON = async function (boardID, profileID) {

  return new Promise((resolve, reject) => {

    try {

      var DirectoryJSON = {
        audio: null,
        video: null,
      };

      this.listMedia(boardID, profileID, 'audio')
        .then(results => {
          DirectoryJSON.audio = results.map(function (item) {
            delete item["board"];
            delete item["profile"];
            return item
          });
          this.listMedia(boardID, profileID, 'video')
            .then(results => {
              DirectoryJSON.video = results.map(function (item) {
                delete item["board"];
                delete item["profile"];
                return item
              });
              return resolve(DirectoryJSON);
            })
        })
        .catch(err => {
          return reject(err);
        });

    }
    catch (err) {
      reject(err);
    }

  });




}

exports.reorderMedia = async function (boardID, profileID, mediaType, mediaArray) {

  return new Promise((resolve, reject) => {
    this.listMedia(boardID, profileID, mediaType)
      .then(results => {
        for (var i = 0; i < mediaArray.length; i++) {

          var result = results.filter(function (element) {
            if (element.algorithm != null)
              return element.algorithm === mediaArray[i];
            else
              return element.localName === mediaArray[i];
            console.log("found" + mediaArray[i])
          });

          result[0].ordinal = i;
        }

        datastore.save(results)
          .then(() => {
            resolve(this.listMedia(boardID, profileID, mediaType));
          })
          .catch(err => {
            reject(err);
          });
      })
  });
}