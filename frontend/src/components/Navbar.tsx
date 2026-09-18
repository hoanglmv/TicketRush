import { Link, useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
import { useState, useEffect, useRef } from 'react';
import { eventApi } from '../api';
import { EventResponse } from '../types';
import { useLanguage } from '../i18n';
import { Search } from 'lucide-react';

export default function Navbar() {
  const { user, logout, isAdmin, isAuthenticated } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const searchParams = new URLSearchParams(location.search);
  const currentCategory = searchParams.get('category') || '';

  const [searchQuery, setSearchQuery] = useState('');
  const [suggestions, setSuggestions] = useState<EventResponse[]>([]);
  const [showSuggestions, setShowSuggestions] = useState(false);
  const searchRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (searchQuery.trim().length >= 2) {
      const delayFn = setTimeout(() => {
        eventApi.search(searchQuery).then(res => {
          setSuggestions(res.data.data || []);
          setShowSuggestions(true);
        }).catch(() => {});
      }, 300);
      return () => clearTimeout(delayFn);
    } else {
      setSuggestions([]);
      setShowSuggestions(false);
    }
  }, [searchQuery]);

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (searchRef.current && !searchRef.current.contains(event.target as Node)) {
        setShowSuggestions(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const { t, locale, toggleLocale } = useLanguage();

  const isCategoryActive = (cat: string) => {
    return currentCategory === cat;
  };

  return (
    <nav className="w-full sticky top-0 z-[1000] bg-[#111111]/90 backdrop-blur-md border-b border-white/5 transition-all duration-300">
      <div className="container mx-auto px-6 max-w-[95%]">
        {/* Main Bar */}
        <div className="flex items-center justify-between py-4 gap-8">
          {/* Logo */}
          <Link to="/" className="flex items-center gap-2 group">
            <div className="text-2xl font-black tracking-tighter text-white">
              <span className="italic">ticket</span>
              <span className="text-[#00b14f]">rush</span>
            </div>
          </Link>
          
          {/* Search Bar */}
          <div className="hidden md:flex items-center flex-1 max-w-xl relative group" ref={searchRef}>
            <div className="absolute inset-y-0 left-4 flex items-center pointer-events-none text-white/30 group-focus-within:text-[#00b14f] transition-colors">
              <Search size={18} />
            </div>
            <input 
              type="text" 
              placeholder={t('nav.searchPlaceholder')}
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              onFocus={() => { if (suggestions.length > 0) setShowSuggestions(true); }}
              className="w-full bg-white/5 border border-white/10 rounded-full py-2.5 pl-11 pr-24 text-sm text-white focus:outline-none focus:border-[#00b14f] focus:bg-white/10 transition-all placeholder:text-white/20"
              onKeyDown={(e) => {
                if (e.key === 'Enter' && searchQuery.trim()) {
                  setShowSuggestions(false);
                  navigate(`/events?search=${encodeURIComponent(searchQuery)}`);
                }
              }}
            />
            <button 
              className="absolute right-1.5 top-1.5 bottom-1.5 px-6 bg-[#00b14f] text-white text-[11px] font-black uppercase rounded-full hover:bg-[#008a3d] transition-all tracking-wider"
              onClick={() => {
                if (searchQuery.trim()) {
                  setShowSuggestions(false);
                  navigate(`/events?search=${encodeURIComponent(searchQuery)}`);
                }
              }}
            >
              {t('nav.search')}
            </button>

            {/* Suggestions Dropdown */}
            {showSuggestions && suggestions.length > 0 && (
              <div className="absolute top-full left-0 right-0 w-full bg-[#1a1a1a] rounded-2xl shadow-2xl border border-white/5 z-50 max-h-[350px] overflow-y-auto mt-2 overflow-hidden">
                <div className="px-5 py-3 text-[10px] font-black text-white/40 uppercase tracking-widest border-b border-white/5">{t('search.suggestedEvents')} ({suggestions.length})</div>
                {suggestions.map(event => (
                  <div 
                    key={event.id}
                    className="flex px-5 py-3 border-b border-white/5 cursor-pointer items-center hover:bg-white/5 transition-colors"
                    onClick={() => {
                      setShowSuggestions(false);
                      setSearchQuery('');
                      navigate(`/events/${event.id}`);
                    }}
                  >
                    <div className="w-10 h-10 rounded-lg bg-cover bg-center mr-4 flex-shrink-0" style={{ backgroundImage: `url(${event.bannerUrl})` }} />
                    <div className="flex-1 min-w-0">
                      <div className="font-bold text-sm text-white truncate">{event.name}</div>
                      <div className="text-[10px] text-white/50 font-bold uppercase tracking-wider truncate mt-1">{event.city} • {event.venue}</div>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>

          {/* User Actions */}
          <div className="flex items-center gap-4">
            {isAdmin && (
              <Link to="/admin/events/create" className="hidden lg:flex items-center gap-2 text-white/70 hover:text-white transition-colors">
                <div className="p-1.5 bg-white/5 rounded-lg border border-white/10 group-hover:border-[#00b14f] transition-colors">
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" className="text-[#00b14f]"><line x1="12" y1="5" x2="12" y2="19"></line><line x1="5" y1="12" x2="19" y2="12"></line></svg>
                </div>
                <span className="text-[11px] font-black uppercase tracking-widest">{t('nav.createEvent')}</span>
              </Link>
            )}
            
            {isAuthenticated ? (
              <div className="flex items-center gap-6">
                <Link to="/tickets" className="text-white/70 text-[11px] font-black uppercase tracking-widest flex items-center gap-2 hover:text-[#00b14f] transition-colors">
                   <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className="text-[#00b14f]"><path d="M4 15s1-1 4-1 5 2 8 2 4-1 4-1V3s-1 1-4 1-5-2-8-2-4 1-4 1z"></path><line x1="4" y1="22" x2="4" y2="15"></line></svg>
                   {t('nav.myTickets')}
                </Link>
                
                <div className="relative group">
                  <div className="flex items-center gap-3 cursor-pointer py-1">
                    <div className="w-9 h-9 rounded-full bg-gradient-to-br from-[#00b14f] to-[#008a3d] p-[2px] shadow-lg shadow-[#00b14f]/20 group-hover:scale-105 transition-transform">
                      <div className="w-full h-full rounded-full bg-black flex items-center justify-center font-black text-xs text-white">
                        {user?.username?.charAt(0).toUpperCase()}
                      </div>
                    </div>
                    <div className="hidden sm:block">
                      <div className="text-[9px] text-white/40 uppercase font-black tracking-widest leading-none mb-1">{isAdmin ? 'Admin' : 'Member'}</div>
                      <div className="text-xs font-bold text-white group-hover:text-[#00b14f] transition-colors">{user?.username}</div>
                    </div>
                  </div>
                  {/* Dropdown */}
                  <div className="absolute right-0 top-full mt-2 w-56 bg-[#1a1a1a] rounded-2xl shadow-2xl border border-white/5 p-2 opacity-0 invisible group-hover:opacity-100 group-hover:visible transition-all duration-300 z-50">
                    <Link to="/profile" className="flex items-center gap-3 px-4 py-3 text-xs text-white/70 hover:text-white hover:bg-white/5 rounded-xl transition-all font-bold uppercase tracking-wider">{t('nav.myProfile')}</Link>
                    {isAdmin && <Link to="/admin" className="flex items-center gap-3 px-4 py-3 text-xs text-white/70 hover:text-white hover:bg-white/5 rounded-xl transition-all font-bold uppercase tracking-wider">{t('nav.adminDashboard')}</Link>}
                    <div className="h-px bg-white/5 my-1 mx-2"></div>
                    <button onClick={logout} className="w-full flex items-center gap-3 px-4 py-3 text-xs text-red-400 hover:bg-red-400/10 rounded-xl transition-all font-bold uppercase tracking-wider">{t('nav.signOut')}</button>
                  </div>
                </div>
              </div>
            ) : (
              <Link to="/login" className="px-6 py-2 bg-[#00b14f] text-white text-[11px] font-black uppercase tracking-widest rounded-full hover:bg-[#008a3d] transition-all shadow-lg shadow-[#00b14f]/20">
                {t('nav.signInRegister')}
              </Link>
            )}

            <button
              onClick={toggleLocale}
              className="flex items-center gap-1.5 px-3 py-1.5 bg-white/5 hover:bg-white/10 border border-white/10 rounded-full transition-all ml-2"
            >
              {locale === 'vi' ? (
                <img src="https://flagcdn.com/w40/vn.png" className="w-4 h-3 object-cover rounded-sm" alt="VI" />
              ) : (
                <img src="https://flagcdn.com/w40/us.png" className="w-4 h-3 object-cover rounded-sm" alt="EN" />
              )}
              <span className="text-[10px] font-bold text-white/40 uppercase">{locale}</span>
            </button>
          </div>
        </div>

        {/* Categories Bar */}
        <div className="border-t border-white/5 overflow-x-auto no-scrollbar">
          <ul className="flex items-center justify-center gap-10 py-3 text-[10px] font-black uppercase tracking-[0.2em]">
            <li><Link to="/events?category=LIVE_MUSIC" className={`${isCategoryActive('LIVE_MUSIC') ? 'text-[#00b14f]' : 'text-white/40'} hover:text-white transition-colors whitespace-nowrap`}>{t('nav.concerts')}</Link></li>
            <li><Link to="/events?category=ARTS" className={`${isCategoryActive('ARTS') ? 'text-[#00b14f]' : 'text-white/40'} hover:text-[#00b14f] transition-colors whitespace-nowrap`}>{t('nav.arts')}</Link></li>
            <li><Link to="/events?category=SPORTS" className={`${isCategoryActive('SPORTS') ? 'text-[#00b14f]' : 'text-white/40'} hover:text-[#00b14f] transition-colors whitespace-nowrap`}>{t('nav.sports')}</Link></li>
            <li><Link to="/events?category=WORKSHOP" className={`${isCategoryActive('WORKSHOP') ? 'text-[#00b14f]' : 'text-white/40'} hover:text-[#00b14f] transition-colors whitespace-nowrap`}>{t('nav.workshop')}</Link></li>
            <li><Link to="/events?category=EXPERIENCE" className={`${isCategoryActive('EXPERIENCE') ? 'text-[#00b14f]' : 'text-white/40'} hover:text-[#00b14f] transition-colors whitespace-nowrap`}>{t('nav.experience')}</Link></li>
            <li><Link to="/events?category=OTHER" className={`${isCategoryActive('OTHER') ? 'text-[#00b14f]' : 'text-white/40'} hover:text-[#00b14f] transition-colors whitespace-nowrap`}>{t('nav.other')}</Link></li>
          </ul>
        </div>
      </div>
    </nav>
  );
}
