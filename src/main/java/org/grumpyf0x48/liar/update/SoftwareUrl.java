package org.grumpyf0x48.liar.update;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.grumpyf0x48.liar.update.exceptions.SoftwareException;
import org.grumpyf0x48.liar.update.exceptions.SoftwareUrlNotParsableException;
import org.grumpyf0x48.misc.NetworkUtils;

import java.net.ConnectException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SoftwareUrl implements SoftwareIncrementable<SoftwareUrl>
{
    private final SoftwareDefinition software;
    private final SoftwareVersion version;
    private final SoftwareUpdateOptions updateOptions;

    private String url;

    public SoftwareUrl(final SoftwareDefinition software, final String url) throws SoftwareException
    {
        this(software, url, SoftwareUpdateOptions.withDefault());
    }

    public SoftwareUrl(final String url, final SoftwareUpdateOptions updateOptions) throws SoftwareException
    {
        this(null, url, updateOptions);
    }

    public SoftwareUrl(final SoftwareDefinition software, final String url, final SoftwareUpdateOptions updateOptions) throws SoftwareException
    {
        this.software = software;
        this.version = parse(url, updateOptions);
        this.updateOptions = updateOptions;
        this.url = url;
    }

    public SoftwareDefinition getSoftware()
    {
        return software;
    }

    public SoftwareVersion getVersion()
    {
        return version;
    }

    public String getUrl()
    {
        return url;
    }

    @Override
    public SoftwareUrl getNext() throws SoftwareException
    {
        switch (updateOptions.urlIncrementPolicy)
        {
            case NEXT:
                return nextUrl();
            case NEXT_EXISTING:
                nextUrl();
                return nextExistingUrl();
            case LAST_EXISTING:
                nextUrl();
                return lastExistingUrl();
            default:
                throw new IllegalArgumentException("Invalid urlIncrementPolicy: " + updateOptions.urlIncrementPolicy);
        }
    }

    private SoftwareUrl nextUrl() throws SoftwareException
    {
        url = url.replaceAll(version.toString(), version.getNext().toString());
        return this;
    }

    private SoftwareUrl nextExistingUrl() throws SoftwareException
    {
        int maxTries = 0;
        try
        {
            while (!NetworkUtils.urlExists(url))
            {
                nextUrl();
                maxTries++;
            }
            return this;
        }
        catch (final SoftwareException softwareException)
        {
            throw softwareException.setMaxTries(maxTries);
        }
        catch (final ConnectException connectException)
        {
            throw new SoftwareException("Failed to connect to: " + url, connectException).setMaxTries(maxTries);
        }
    }

    private SoftwareUrl lastExistingUrl() throws SoftwareException
    {
        SoftwareUrl lastExistingUrl = null;
        for (; ;)
        {
            try
            {
                if (NetworkUtils.urlExists(url))
                {
                    nextUrl();
                }
                else
                {
                    nextExistingUrl();
                }
                if (NetworkUtils.urlExists(url))
                {
                    lastExistingUrl = new SoftwareUrl(software, url);
                }
            }
            catch (final SoftwareException softwareException)
            {
                if (lastExistingUrl != null)
                {
                    return lastExistingUrl;
                }
                throw softwareException;
            }
            catch (final ConnectException connectException)
            {
                throw new SoftwareException("Failed to connect to: " + url, connectException);
            }
        }
    }

    @Override
    public boolean equals(final Object o)
    {
        if (this == o)
        {
            return true;
        }

        if (o == null || getClass() != o.getClass())
        {
            return false;
        }

        final SoftwareUrl that = (SoftwareUrl) o;

        return new EqualsBuilder() //
                        .append(url, that.url) //
                        .isEquals();
    }

    @Override
    public int hashCode()
    {
        return new HashCodeBuilder(17, 37) //
                        .append(url) //
                        .toHashCode();
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this) //
                        .append("url", url) //
                        .toString();
    }

    private static SoftwareVersion parse(final String url, final SoftwareUpdateOptions updateOptions) throws SoftwareException
    {
        for (final String versionRegexp : SoftwareVersion.getParsableRegexps())
        {
            final String urlRegexp = "[^\\d]*(?<version>" + versionRegexp + ").*(\\k<version>)?[^\\d]*";
            final Pattern urlPattern = Pattern.compile(urlRegexp);
            final Matcher urlMatcher = urlPattern.matcher(url);
            if (urlMatcher.matches())
            {
                return new SoftwareVersion(urlMatcher.group("version"), updateOptions.versionIncrementPolicy);
            }
        }

        throw new SoftwareUrlNotParsableException(url + " is not parsable");
    }
}
