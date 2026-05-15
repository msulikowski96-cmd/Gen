import React, { useState } from 'react';

export default function App() {
  const [nickname, setNickname] = useState('');
  const [image, setImage] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const generateCard = async () => {
    if (!nickname) return;
    setLoading(true);
    try {
      const response = await fetch('/api/generate', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ nickname }),
      });
      if (!response.ok) {
        throw new Error('Network response was not ok');
      }
      const blob = await response.blob();
      setImage(URL.createObjectURL(blob));
    } catch (err) {
      alert('Error generating card!');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex flex-col items-center justify-center p-4 bg-slate-900 text-white">
      <h1 className="text-5xl font-extrabold mb-8 text-transparent bg-clip-text bg-gradient-to-r from-blue-400 to-purple-600">
        Nexus Sight Card Gen
      </h1>
      <div className="flex gap-2 mb-12 w-full max-w-md">
        <input
          type="text"
          className="flex-1 px-4 py-3 rounded-lg bg-slate-800 border border-slate-700 text-white focus:outline-none focus:ring-2 focus:ring-blue-500"
          placeholder="Enter Nickname..."
          value={nickname}
          onChange={(e) => setNickname(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && generateCard()}
        />
        <button
          onClick={generateCard}
          disabled={loading || !nickname}
          className="px-6 py-3 bg-blue-600 hover:bg-blue-500 rounded-lg font-bold transition-all disabled:opacity-50"
        >
          {loading ? '...' : 'Generate'}
        </button>
      </div>

      {image && (
        <div className="flex flex-col items-center">
          <img src={image} alt="Player Card" className="rounded-2xl shadow-2xl border-4 border-slate-700 max-w-xs" />
          <a href={image} download="card.png" className="mt-6 px-8 py-3 bg-green-600 hover:bg-green-500 rounded-full font-bold transition-all">
            Download
          </a>
        </div>
      )}
    </div>
  );
}
