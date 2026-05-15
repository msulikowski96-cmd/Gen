import React, { useState } from 'react';
import { Loader2, Download, AlertCircle } from 'lucide-react';

export default function App() {
  const [nickname, setNickname] = useState('');
  const [image, setImage] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const generateCard = async () => {
    if (!nickname) return;
    setLoading(true);
    setError(null);
    setImage(null);
    try {
      const response = await fetch('/api/generate', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ nickname }),
      });
      if (!response.ok) {
        let errorMsg = 'Failed to generate card. Please try again.';
        try {
          const errData = await response.json();
          if (errData.error) errorMsg = errData.error;
        } catch (e) {}
        throw new Error(errorMsg);
      }
      const blob = await response.blob();
      setImage(URL.createObjectURL(blob));
    } catch (err: any) {
      setError(err.message || 'Error generating card!');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex flex-col items-center justify-center p-4 bg-slate-900 text-white font-sans">
      <div className="absolute inset-0 bg-[url('https://images.unsplash.com/photo-1542751371-adc38448a05e?auto=format&fit=crop&q=80')] bg-cover bg-center opacity-10 pointer-events-none" />
      
      <div className="relative z-10 flex flex-col items-center w-full max-w-lg">
        <h1 className="text-4xl md:text-5xl font-extrabold mb-2 text-transparent bg-clip-text bg-gradient-to-r from-blue-400 via-indigo-500 to-purple-600 text-center drop-shadow-sm">
          Nexus Sight
        </h1>
        <p className="text-slate-400 mb-8 font-medium">Dynamic Player Card Generator</p>
        
        <div className="flex flex-col sm:flex-row gap-3 mb-8 w-full">
          <input
            type="text"
            className="flex-1 px-5 py-4 rounded-xl bg-slate-800/80 backdrop-blur border border-slate-700 text-white placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-indigo-500 shadow-xl transition-all"
            placeholder="Enter Summoner Name..."
            value={nickname}
            onChange={(e) => setNickname(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && generateCard()}
            spellCheck={false}
          />
          <button
            onClick={generateCard}
            disabled={loading || !nickname}
            className="px-8 py-4 bg-indigo-600 hover:bg-indigo-500 rounded-xl font-bold transition-all disabled:opacity-50 disabled:hover:bg-indigo-600 shadow-[0_0_20px_rgba(79,70,229,0.3)] hover:shadow-[0_0_25px_rgba(79,70,229,0.5)] flex items-center justify-center min-w-[140px]"
          >
            {loading ? <Loader2 className="w-6 h-6 animate-spin" /> : 'Generate'}
          </button>
        </div>

        {error && (
          <div className="mb-8 w-full p-4 rounded-xl bg-red-500/10 border border-red-500/20 flex items-start gap-3 text-red-200">
            <AlertCircle className="w-6 h-6 shrink-0 text-red-400" />
            <p className="text-sm font-medium pt-0.5">{error}</p>
          </div>
        )}

        <div className="w-full flex flex-col items-center min-h-[600px] justify-center">
          {loading && (
            <div className="w-[400px] h-[600px] rounded-3xl bg-slate-800/50 backdrop-blur border border-slate-700/50 flex flex-col items-center justify-center shadow-2xl animate-pulse">
              <Loader2 className="w-12 h-12 animate-spin text-indigo-500 mb-6" />
              <div className="h-6 w-48 bg-slate-700 rounded-full mb-4"></div>
              <div className="h-4 w-32 bg-slate-700 rounded-full"></div>
            </div>
          )}

          {!loading && image && (
            <div className="flex flex-col items-center animate-in fade-in zoom-in duration-500 ease-out">
              <div className="relative group">
                <img 
                  src={image} 
                  alt="Player Card" 
                  className="w-[400px] h-[600px] rounded-3xl shadow-[0_0_50px_rgba(0,0,0,0.5)] border-4 border-slate-800/50 object-contain" 
                />
                <div className="absolute inset-0 rounded-3xl bg-black/40 opacity-0 group-hover:opacity-100 backdrop-blur-sm transition-all duration-300 flex items-center justify-center">
                  <a 
                    href={image} 
                    download={`${nickname}-card.png`} 
                    className="flex items-center gap-2 px-8 py-4 bg-white text-slate-900 rounded-full font-bold hover:scale-105 transition-transform shadow-2xl"
                  >
                    <Download className="w-5 h-5" />
                    Download Card
                  </a>
                </div>
              </div>
              
              <a 
                href={image} 
                download={`${nickname}-card.png`} 
                className="mt-8 flex items-center gap-2 px-8 py-4 bg-gradient-to-r from-emerald-500 to-emerald-600 hover:from-emerald-400 hover:to-emerald-500 text-white rounded-xl font-bold transition-all shadow-[0_0_20px_rgba(16,185,129,0.3)] hover:shadow-[0_0_30px_rgba(16,185,129,0.5)] sm:hidden"
              >
                <Download className="w-5 h-5" />
                Save Image
              </a>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
