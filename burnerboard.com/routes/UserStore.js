const constants = require("./Constants");
const GoogleAuth = require("google-auth-library");
const Datastore = require("@google-cloud/datastore");
const datastore = new Datastore({
	projectId: constants.PROJECT_ID,
});

exports.verifyJWT = async function (JWT) {
	return new Promise((resolve, reject) => {

		var auth = new GoogleAuth;
		var client = new auth.OAuth2(constants.CLIENT_ID, "", "");
		client.verifyIdToken(
			JWT,
			constants.CLIENT_ID,
			function (e, login) {
				if (e) {
					return reject(e);
				}
				else {
					var payload = login.getPayload();
					var email = payload.email;
					var emailFound = null;

					const emailExists = datastore.createQuery("user")
						.filter("email", "=", email);

					datastore
						.runQuery(emailExists)
						.then(results => {
							emailFound = (results[0].length > 0);

							if (emailFound) {
								return resolve(JWT);
							}
							else {
								return reject(new Error("Not Authorized for BurnerBoard.com"));
							}
						})
						.catch(err => {
							reject(err);
						});
				}
			});
	});
};