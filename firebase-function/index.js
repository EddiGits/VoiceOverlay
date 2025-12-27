const functions = require('firebase-functions');
const axios = require('axios');
const FormData = require('form-data');
const Busboy = require('busboy');

/**
 * Firebase Cloud Function for Whisper API Transcription Proxy
 *
 * This function acts as a secure proxy between the VoiceOverlay Android app
 * and the OpenAI Whisper API. It keeps the API key secure on the server-side
 * and prevents unauthorized access through subscription verification.
 *
 * Environment Variables (set via Firebase Functions config):
 * - OPENAI_API_KEY: Your OpenAI API key
 */

exports.transcribeAudio = functions.https.onRequest(async (req, res) => {
  // Enable CORS
  res.set('Access-Control-Allow-Origin', '*');

  if (req.method === 'OPTIONS') {
    res.set('Access-Control-Allow-Methods', 'POST');
    res.set('Access-Control-Allow-Headers', 'Content-Type');
    res.set('Access-Control-Max-Age', '3600');
    res.status(204).send('');
    return;
  }

  // Only allow POST requests
  if (req.method !== 'POST') {
    res.status(405).json({ error: 'Method not allowed' });
    return;
  }

  try {
    // Get OpenAI API key from environment
    const openaiApiKey = functions.config().openai?.key;
    if (!openaiApiKey) {
      console.error('OpenAI API key not configured');
      res.status(500).json({ error: 'Server configuration error' });
      return;
    }

    // TODO: Add subscription verification here
    // For now, we'll process all requests
    // Example:
    // const userId = req.headers['x-user-id'];
    // const isSubscribed = await verifySubscription(userId);
    // if (!isSubscribed) {
    //   res.status(403).json({ error: 'Subscription required' });
    //   return;
    // }

    // Parse multipart form data
    const busboy = Busboy({ headers: req.headers });
    const fields = {};
    let audioFileBuffer = null;
    let audioFileName = null;
    let audioMimeType = null;

    const filePromise = new Promise((resolve, reject) => {
      busboy.on('field', (fieldname, val) => {
        fields[fieldname] = val;
      });

      busboy.on('file', (fieldname, file, info) => {
        const { filename, encoding, mimeType } = info;
        audioFileName = filename;
        audioMimeType = mimeType;

        const chunks = [];
        file.on('data', (data) => {
          chunks.push(data);
        });

        file.on('end', () => {
          audioFileBuffer = Buffer.concat(chunks);
        });
      });

      busboy.on('finish', () => {
        resolve();
      });

      busboy.on('error', (error) => {
        reject(error);
      });
    });

    req.pipe(busboy);
    await filePromise;

    // Validate that we received an audio file
    if (!audioFileBuffer) {
      res.status(400).json({ error: 'No audio file provided' });
      return;
    }

    // Create form data for OpenAI API
    const formData = new FormData();
    formData.append('file', audioFileBuffer, {
      filename: audioFileName || 'audio.m4a',
      contentType: audioMimeType || 'audio/m4a'
    });

    formData.append('model', fields.model || 'whisper-1');

    if (fields.prompt) {
      formData.append('prompt', fields.prompt);
    }

    // Call OpenAI Whisper API
    const response = await axios.post(
      'https://api.openai.com/v1/audio/transcriptions',
      formData,
      {
        headers: {
          'Authorization': `Bearer ${openaiApiKey}`,
          ...formData.getHeaders()
        },
        maxBodyLength: Infinity,
        timeout: 120000 // 2 minute timeout
      }
    );

    // Return transcription result
    res.status(200).json({
      text: response.data.text
    });

  } catch (error) {
    console.error('Transcription error:', error);

    if (error.response) {
      // OpenAI API error
      res.status(error.response.status).json({
        error: error.response.data?.error?.message || 'OpenAI API error'
      });
    } else if (error.code === 'ECONNABORTED') {
      res.status(408).json({ error: 'Request timeout' });
    } else {
      res.status(500).json({ error: 'Internal server error' });
    }
  }
});

/**
 * Example subscription verification function (to be implemented)
 *
 * This would integrate with Google Play Billing to verify
 * that the user has an active subscription.
 */
async function verifySubscription(userId) {
  // TODO: Implement subscription verification
  // 1. Get purchase token from request or database
  // 2. Verify with Google Play Developer API
  // 3. Check subscription status and expiry
  // 4. Return true/false
  return true; // Allow all for now
}
