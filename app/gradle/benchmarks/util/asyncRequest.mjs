import https from 'https';

export default function asyncRequest(url, options, data) {
	return new Promise((resolve, reject) => {
		const request = https.request(url, options, (response) => {
			response.setEncoding('utf8');
			let responseBody = '';

			response.on('data', (chunk) => {
				responseBody += chunk;
			});

			response.on('end', () => {
				resolve(JSON.parse(responseBody));
			});
		});

		request.on('error', (error) => {
			reject(error);
		});

		request.write(data);
		request.end();
	});
}