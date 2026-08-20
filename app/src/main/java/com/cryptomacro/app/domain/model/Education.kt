package com.cryptomacro.app.domain.model

/** BEGINNER: In-app lesson text (title, subtitle, body paragraphs, takeaway). Learn → Lessons reads this list. */
object EducationCatalog {
    val modules = listOf(
        EducationModule(
            id = "cycles",
            title = "Market cycle histories",
            subtitle = "Halvings, liquidity, and 7-year overlays",
            body = listOf(
                "Crypto and equity markets move in liquidity-driven cycles. Bitcoin’s programmed halvings (~every 4 years) have historically preceded multi-year expansions, but they are not a timing trigger by themselves.",
                "The Shemitah 7-year sabbatical overlay is a research heuristic used by some macro observers: stress has clustered near certain cycle-end windows (1987, 2001, 2008, 2015, 2022). Correlation is not causation.",
                "Use cycle maps as context for risk budgeting — cash buffers, position size, and time horizon — not as buy/sell signals.",
            ),
            takeaway = "Cycles describe regimes. They do not replace valuation, liquidity, or your own time horizon.",
        ),
        EducationModule(
            id = "risk",
            title = "Risk mitigation",
            subtitle = "Position sizing, drawdowns, and cash buffers",
            body = listOf(
                "Decide max portfolio drawdown you can tolerate before you buy. A 50% crash on a 100% allocation is a different life event than a 50% crash on a 20% allocation.",
                "Dollar-cost averaging (DCA) reduces timing luck. Compare your actual lots against a mechanical weekly/monthly buy of the same cash — that is what the in-app DCA calculator does.",
                "Avoid concentrating in a single high-beta alt. Rebalance toward quality (BTC/ETH, cash, or broad equity) when one name dominates allocation.",
                "Keep an emergency fiat buffer outside the portfolio so you are never a forced seller.",
            ),
            takeaway = "Survive first. Compounding only works if you stay in the game.",
        ),
        EducationModule(
            id = "custody",
            title = "Self-custody principles",
            subtitle = "Keys, backups, and watch-only tracking",
            body = listOf(
                "Not your keys, not your coins. Exchange balances are IOUs. Long-term holdings belong in wallets you control (hardware preferred).",
                "Seed phrases are bearer instruments. Write them on paper or metal. Never screenshot, email, or cloud-sync them. This app never asks for a seed or private key.",
                "Watch-only addresses (xPub/zPub or a single BTC/ETH address) let you track balances without signing authority. That is the safe way to monitor self-custody here.",
                "Test a small restore on a spare device before you rely on a backup. Split backups geographically if the stack is material.",
            ),
            takeaway = "This app is a viewer and journal — never a custodian and never a keystore for seeds.",
        ),
        EducationModule(
            id = "2fa",
            title = "2FA hardening",
            subtitle = "Phishing-resistant unlock and exchange logins",
            body = listOf(
                "SMS 2FA is phishable. Prefer hardware security keys (FIDO2 / YubiKey) or an authenticator app stored on a dedicated device.",
                "Enable biometric app lock and FLAG_SECURE (privacy shield) so balances are not visible in recents or screenshots.",
                "For exchanges, store API keys as read-only, withdraw-disabled, IP-restricted. This app encrypts keys on-device with the Android Keystore — they never leave the phone.",
                "Passkeys / WebAuthn beat passwords. Use them wherever an exchange or email provider supports them.",
            ),
            takeaway = "Assume phishing. Prefer keys and biometrics over codes that can be typed into a fake site.",
        ),
        EducationModule(
            id = "leverage",
            title = "Avoiding high-leverage traps",
            subtitle = "Liquidations, funding, and retail blow-ups",
            body = listOf(
                "Perpetual futures with 10–100× leverage convert normal volatility into account death. Bitcoin routinely moves 5–10% in a day — that is a full wipe at high leverage.",
                "Funding rates and liquidation cascades amplify moves. If you do not understand the product, you are the liquidity.",
                "HODL and DCA strategies in this app assume spot ownership. They are incompatible with leveraged bets that can go to zero overnight.",
                "If you still use derivatives, cap risk per trade to a fraction of portfolio you can lose without changing your life — and never add margin to a losing leveraged position.",
            ),
            takeaway = "Leverage is optional. Ruin is not a required part of learning.",
        ),
    )
}
