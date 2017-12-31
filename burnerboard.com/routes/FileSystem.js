const Storage = require('@google-cloud/storage');
const storage = Storage();
const bucketName = 'burner-board';
const google = require('googleapis');
const drive = google.drive('v2');
const bucket = storage.bucket('burner-board');

const MUSIC_PATH = "BurnerBoardMedia";

var fileAttributes = {
	fileSize: 0,
	mimeType: "",
	title: "",
	songDuration: 0,
	speechCue: 0,
};

exports.addGDriveFile = function (boardID, fileId, oauthToken, speechCue, callback) {

	var tempFileName = "";

	drive.files.get({
		fileId: fileId,
		'access_token': oauthToken,
		//	alt: 'media'
	}, function (err, jsonContent) {

		if (err)
			callback(err, null);
		else {
			fileAttributes = {
				fileSize: jsonContent.fileSize,
				mimeType: jsonContent.mimeType,
				title: jsonContent.title,
				songDuration: 0,
				speechCue: "",
			};

			drive.files.get({
				fileId: fileId,
				'access_token': oauthToken,
				alt: 'media'
			}, function (err, content) {

				if (err)
					callback(err, null);
				else {
					// now get the real file and save it.
					var filePath = 'BurnerBoardMedia' + '/' + boardID + '/' + fileAttributes.title;
					var file3 = bucket.file(filePath);
					var fileStream3 = file3.createWriteStream({
						metadata: {
							contentType: fileAttributes.mimeType,
						}
					});

					fileStream3.on('error', (err) => {
						callback(err, null);
					});

					fileStream3.on('finish', () => {
						file3.makePublic();

						// now we need to add a record to directory json
						DownloadDirectory = require('./DownloadDirectory');
						if (filePath.endsWith('mp3'))
							DownloadDirectory.addAudio(boardID,
								filePath,
								fileAttributes.fileSize,
								fileAttributes.songDuration,
								callback);
						else if (filePath.endsWith('mp4'))
							DownloadDirectory.addVideo(boardID,
								filePath,
								fileAttributes.speechCue,
								callback);
					});
					fileStream3.end(content);
				};
			});
		}
	});

}

exports.addFile = function (boardID, uploadedFile, speechCue, callback) {
	var contenttype = '';
	var songDuration;
	var fileSize = uploadedFile.size;
	var localName = uploadedFile.originalname;

	if (uploadedFile.originalname.endsWith('mp3')) {

		var mp3Duration = require('mp3-duration');
		mp3Duration(uploadedFile.buffer, function (err, duration) {
			if (err) {
				callback(err);
			}
			songDuration = duration;
		});
		contenttype = 'audio/mpeg';
	}
	else if (uploadedFile.originalname.endsWith('mp4'))
		contenttype = 'video/mp4';

	var filepath = MUSIC_PATH + '/' + boardID + '/' + uploadedFile.originalname;

	const file = bucket.file(filepath);
	const fileStream = file.createWriteStream({
		metadata: {
			contentType: contenttype
		}
	});

	fileStream.on('error', (err) => {
		callback(err);
	});

	fileStream.on('finish', () => {

		DownloadDirectory = require('./DownloadDirectory');
		if (uploadedFile.originalname.endsWith('mp3'))
			DownloadDirectory.addAudio(boardID,
				file.name,
				file.Size,
				songDuration,
				function (err) {
					if (!err) {
						bucket
							.file(filepath)
							.makePublic()
							.then(() => {
								callback(null, { "localName": localName, "Size": fileSize, "Length": songDuration });
							})
							.catch(err => {
								callback(err, null);
							});

					}
					else {
						callback(err, null);
					}
				});
		else if (uploadedFile.originalname.endsWith('mp4'))
			DownloadDirectory.addVideo(boardID,
				file.name,
				speechCue,
				function (err) {
					if (!err) {
						bucket
							.file(filepath)
							.makePublic()
							.then(() => {
								callback(null, { "localName": localName, "speachCue": speechCue });
							})
							.catch(err => {
								callback(err, null);
							});

					}
					else {
						callback(err, null);
					}
				});

	});

	fileStream.end(uploadedFile.buffer);
}

exports.listFiles = function (boardID, callback) {

	bucket
		.getFiles({
			autoPaginate: false,
			delimiter: '/',
			prefix: MUSIC_PATH + '/' + boardID + '/'
		}, function (err, files, nextQuery, apiResponse) {
			if (err) {
				callback(err, null);
			}
			else {
				var fileList = [];
				for (var i = 0; i < files.length; i++) {
					fileList.push(files[i].name);
				}
				callback(null, fileList);
			}
		})

}